package aser.ufo;

import aser.ufo.misc.*;
import aser.ufo.trace.AllocaPair;
import aser.ufo.trace.FileInfo;
import aser.ufo.trace.Indexer;
import aser.ufo.trace.LoadingTask2;
import aser.ufo.trace.TLEventSeq;
import com.google.common.collect.Lists;
import config.Configuration;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import trace.AbstractNode;
import trace.DeallocNode;
import trace.MemAccNode;

import java.util.*;
import java.util.concurrent.*;


public class Session2 extends Session {

    public Session2(Configuration c) throws Exception {
        super(c);

    }

    int loadedEventCount = 0;


    @Override
    public void start() {
        //===================load trace init Reach Engine=============================
        traceLoader.preprocessWaitNotify(this);
        printTraceStats();

        sessionID = 0;
        uafID = 0;

        while (traceLoader.hasNext()) {
            sessionID++;
            //===================load file event analyze init indexer=============================
            Indexer indexer = new Indexer();
            traceLoader.populateIndexer(indexer, this);
            loadedEventCount += indexer.metaInfo.rawNodeCount;



            HashMap<MemAccNode, HashSet<AllocaPair>> candidateUafLs = indexer.getMachtedAcc();


            prepareConstraints(indexer);

            solver.setCurrentIndexer(indexer);

            ct_candidataUaF.add(candidateUafLs.size());

//            List<RacePair> pairList = Lists.newArrayList();
//            RacePair racePair = new RacePair();
//            pairList.add(racePair);
//            // 0 不存在依赖的一对 // 1存在依赖的一对
//            for (AbstractNode node : indexer.getAllNodeSeq()) {
//                if (node.gid == 4294967303L) {
//                    racePair.setFirstRaceAccNode1((MemAccNode) node);
//                    //node.gid = 0;
//                }
//                if (node.gid == 4294967304L) {
//                    racePair.setSecondRaceAccNode2((MemAccNode) node);
//                    //node.gid =2;
//                }
//                if (node.gid == 8589934595L) {
//                    racePair.setSecondRaceAccNode1((MemAccNode) node);
//                    //node.gid =1;
//                }
//                if (node.gid == 8589934596L) {
//                    racePair.setFirstRaceAccNode2((MemAccNode) node);
//                }
//            }

            //===================process handle constr solve=============================
            if ("UAF".equals(config.model)) {
                writerD.append("#").append(String.valueOf(sessionID)).append(" Session").append("   candidateUafLs: ").append(String.valueOf(candidateUafLs.size())).append('\n');
                // UAF
                Iterator<Map.Entry<MemAccNode, HashSet<AllocaPair>>> iter = candidateUafLs.entrySet().iterator();
                while (iter.hasNext()) {
                    List<RawUaf> ls = solveUafConstr(iter, UFO.PAR_LEVEL);
                    if (ls != null && !ls.isEmpty()) outputUafLs(ls, indexer);
                }
            }
            if ("REORDER".equals(config.model)) {
                writerD.append("#").append(String.valueOf(sessionID)).append(" Session").append("   pairList: ").append(String.valueOf(indexer.orderPairList.size())).append('\n');

                // ReOrder
                List<RawOrder> result = solveOrderConstr(indexer.getOrderPairList(), UFO.PAR_LEVEL);
                if (result != null && !result.isEmpty()) {
                    outputOrders(result, indexer);
                }
            }

            //======================print result================
            if (solver.ct_constr.size() > 0) {
                ct_vals.push(solver.ct_vals);
                Pair<Integer, Long> max_total = _Max_total(solver.ct_constr);
                ct_constr.push(max_total.value.intValue());
                if (max_total.value > Integer.MAX_VALUE)
                    throw new RuntimeException("Overflow long -> int " + max_total.value);
                ct_max_constr.push(max_total.key);
            }
            solver.reset(); // release constraints for this round
            writerD.append("\r\n");
        } // while


        _PrintStat();
        exe.shutdown();
        try {
            writerD.close();
            exe.awaitTermination(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.error(" error finishing ", e);
        }
        exe.shutdownNow();
    }


    private HashSet<Pair<Long, Long>> knownUAF = new HashSet<Pair<Long, Long>>(250);

    public HashMap<Integer, Integer> ct_uaf = new HashMap<Integer, Integer>();
    public IntArrayList ct_vals = new IntArrayList(1000);
    public IntArrayList ct_constr = new IntArrayList(1000);
    public IntArrayList ct_max_constr = new IntArrayList(1000);
    public IntArrayList ct_candidataUaF = new IntArrayList(1000);
//  public ArrayList<Pair<Integer, Integer>> ct_constr_max_avg = new ArrayList<Pair<Integer, Integer>>(100);


    public static Pair<Integer, Long> _Max_total(IntArrayList ct_constr) {
        long c = 0;
        int max = 0;
        for (int i : ct_constr) {
            c += i;
            if (i > max) max = i;
        }
        return new Pair<Integer, Long>(max, c);
    }

    public static int _Avg(IntArrayList ct_constr) {
        if (ct_constr == null || ct_constr.size() == 0) return -1;
        long c = 0;
        for (int i : ct_constr)
            c += i;

        return (int) (c / ct_constr.size());
    }


    public void _PrintStat() {
        int max_max = _Max_total(ct_max_constr).key;
        Pair<Integer, Long> mc_constr = _Max_total(ct_constr);

        System.out.println("\r\n=================================\r\n");
        System.out.printf("Session %d | avg vals %d | constr max %d  avg %d | total constr %d | total candidate UaF %d \r\n", sessionID, _Avg(ct_vals), max_max, _Avg(ct_constr), mc_constr.value, _Max_total(ct_candidataUaF).value);

        System.out.println("Solved UAF: " + ct_uaf);
    }

    public void printTraceStats() {
        System.out.println("Start Events: " + TLEventSeq.stat.c_tstart);
        System.out.println("Join Events: " + TLEventSeq.stat.c_join);
        System.out.println("Lock Events: " + TLEventSeq.stat.c_lock);
        System.out.println("Unlock Events: " + TLEventSeq.stat.c_unlock);
        System.out.println("Wait/Notify Events: " + TLEventSeq.stat.c_isync);

        long totalsync = TLEventSeq.stat.c_tstart + TLEventSeq.stat.c_join + TLEventSeq.stat.c_lock + TLEventSeq.stat.c_unlock + TLEventSeq.stat.c_isync;

        System.out.println("Alloc Events: " + TLEventSeq.stat.c_alloc);
        System.out.println("DeAlloc Events: " + TLEventSeq.stat.c_dealloc);

        //total reads
        long reads = TLEventSeq.stat.c_read[0];
        for (int i = 1; i < 4; i++)
            reads += TLEventSeq.stat.c_read[i];

        //total writes
        long writes = TLEventSeq.stat.c_write[0];
        for (int i = 1; i < 4; i++)
            writes += TLEventSeq.stat.c_write[i];

        long toreads = reads + TLEventSeq.stat.c_range_r;
        long towrites = writes + TLEventSeq.stat.c_range_w;

        //long total = totalsync+TLEventSeq.stat.c_alloc+TLEventSeq.stat.c_dealloc+toreads+towrites;

        System.out.println("Total Sync Events: " + totalsync);
        System.out.println("Total Alloc Events: " + TLEventSeq.stat.c_alloc);
        System.out.println("Total USE Events: " + (toreads + towrites));
        System.out.println("Total Free Events: " + TLEventSeq.stat.c_dealloc);
        System.out.println("Total Read Events: " + toreads);
        System.out.println("Total Write Events: " + towrites);
        System.out.println("Total Events: " + TLEventSeq.stat.c_total);
    }

    @Override
    public void outputUafLs(List<RawUaf> uafLs, Indexer indexer) {
        LOG.info("Use-After-Free bugs: {}", uafLs.size());
        for (RawUaf uaf : uafLs) {
            long dePC;
            if (uaf instanceof RawUaFCpx) {
                RawUaFCpx cu = (RawUaFCpx) uaf;
                dePC = cu.pairs.hashCode();
            } else {
                dePC = uaf.deallocNode.pc;
            }
            Pair<Long, Long> pair = new Pair<Long, Long>(dePC, uaf.accNode.pc);
            if (knownUAF.contains(pair)) {
                //System.out.println("Skip known access violation  ");
                continue;
            }
            knownUAF.add(pair);
            uafID++;

            writerD.append("#" + uafID + "  UAF").append("\r\n");
            if (uaf instanceof RawUaFCpx) {
                writerD.append("\r\n\r\n!!!!!!!!! Real UaF   " + ((RawUaFCpx) uaf).pairs.size() + "\r\n");
                System.out.println("\r\n\r\n!!!!!!!!! Real UaF   " + ((RawUaFCpx) uaf).pairs.size() + "\r\n");
            }


            writerD.append("\r\n------- #" + uaf.accNode.tid + " use call stack  \r\n");
            writeCallStack(indexer, uaf.accNode);

            int sz = 1;
            if (uaf instanceof RawUaFCpx) {
                RawUaFCpx cu = (RawUaFCpx) uaf;
                sz = cu.pairs.size();
                for (AllocaPair ap : cu.pairs) {
                    if (ap.deallocNode != null) {
                        writerD.append("\r\n------- #" + uaf.deallocNode.tid + " free call stack  \r\n");
                        writeCallStack(indexer, ap.deallocNode);
                    }
                }
            } else {
                writerD.append("\r\n------- #" + uaf.deallocNode.tid + " free call stack  \r\n");
                writeCallStack(indexer, uaf.deallocNode);
            }

            if (ct_uaf.containsKey(sz)) {
                ct_uaf.put(sz, ct_uaf.get(sz) + 1);
            } else ct_uaf.put(sz, 1);
        }
    }


    public void outputOrders(List<RawOrder> reOrders, Indexer indexer) {
        LOG.info("reOrder bugs: {}", reOrders.size());
        for (RawOrder reOrder : reOrders) {
            writerD.append("\r\n------- #").append(String.valueOf(reOrder.racePair.getNoPair().key.tid)).append(" first  call stack  \r\n");
            writeCallStack(indexer, reOrder.racePair.getNoPair().key);

            writerD.append("\r\n------- #" + reOrder.racePair.getNoPair().value.tid + " second call stack  \r\n");
            writeCallStack(indexer, reOrder.racePair.getNoPair().value);
        }
    }

    protected void _stat(HashMap<MemAccNode, HashSet<AllocaPair>> candidateUafLs) {
        HashMap<Integer, Integer> ct = new HashMap<Integer, Integer>();
        int cm = 0;
        for (HashSet<AllocaPair> p : candidateUafLs.values()) {
            int sz = p.size();
            if (sz > 1) cm++;
            if (ct.containsKey(sz)) ct.put(sz, ct.get(sz) + 1);
            else ct.put(sz, 1);
        }
        System.out.println(((float) cm / candidateUafLs.size()) + "    " + ct);
    }

    public List<RawUaf> solveUafConstr(Iterator<Map.Entry<MemAccNode, HashSet<AllocaPair>>> iter, int limit) {

        CompletionService<RawUaf> cexe = new ExecutorCompletionService<RawUaf>(exe);
        int task = 0;
        while (iter.hasNext() && limit > 0) {
            limit--;
            Map.Entry<MemAccNode, HashSet<AllocaPair>> e = iter.next();
            final MemAccNode acc = e.getKey();
            final HashSet<AllocaPair> pairs = e.getValue();
            cexe.submit(new Callable<RawUaf>() {
                @Override
                public RawUaf call() throws Exception {
                    LongArrayList bugSchedule;
                    if (pairs.size() == 1) {
                        AllocaPair p = pairs.iterator().next();
                        bugSchedule = solver.searchUafSchedule(new Pair<DeallocNode, MemAccNode>(p.deallocNode, acc));
                        if (bugSchedule != null) return new RawUaf(acc, p.deallocNode, bugSchedule);
                        else return null;
                    } else {
                        if (solver.mustUaF(acc, pairs)) return new RawUaFCpx(acc, pairs);
                        else return null;
                    }
                }
            });
            task++;
        }

        ArrayList<RawUaf> ls = new ArrayList<RawUaf>(task);
        try {
            while (task-- > 0) {
                Future<RawUaf> f = cexe.take(); //blocks if none available
                RawUaf uaf = f.get();
                if (uaf != null) ls.add(uaf);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ls;
    }

    public List<RawOrder> solveOrderConstr(List<RacePair> pairList, int limit) {
        ArrayList<RawOrder> ls = new ArrayList<RawOrder>();
        if (pairList == null || pairList.isEmpty()) {
            return ls;
        }
        Iterator<RacePair> iter = pairList.iterator();


        while (iter.hasNext() && limit > 0) {
            limit--;
            LongArrayList bugSchedule = null;
            RacePair e = iter.next();
            bugSchedule = solver.searchReorderSchedule(e);
            if (bugSchedule != null) {
                ls.add(new RawOrder(e, bugSchedule));
            }
        }
        return ls;
    }
}
