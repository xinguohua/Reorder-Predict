package aser.ufo.trace;

import aser.ufo.Session;
import com.alibaba.fastjson.JSONObject;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aser.ufo.NewReachEngine;
import trace.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.Callable;

public class NewLoadingTask implements Callable<TLEventSeq> {

  private static final Logger LOG = LoggerFactory.getLogger(NewLoadingTask.class);

  public final FileInfo fileInfo;

  public NewLoadingTask(FileInfo fi) {
    this.fileInfo = fi;
  }

  public TLEventSeq call() throws Exception {
    return load(null);
  }

  //private long lastIdx;
  public TLEventSeq load(Session s) {
    final short tid = fileInfo.tid;
    TLEventSeq seq = new TLEventSeq(tid);
    NewReachEngine.cur_order_index = 0;//reset order list
    TLHeader header;
    try {
      ByteReader br = new BufferedByteReader();
      br.init(fileInfo);
      int bnext;
      if (fileInfo.fileOffset == 0) {
        bnext = br.read();
        if (bnext != -1) {
          header = getHeader(bnext, br);
          seq.header = header;
        }
      }
      bnext = br.read();

      try {
        System.out.println("##########TheadId#########Tid######" + tid + "############");

        while (bnext != -1) {

          AbstractNode node = getNode(tid, bnext, br, s);
          TLEventSeq.stat.c_total++;
          seq.numOfEvents++;

          if (node != null) {
            System.out.println("TheadId=======Node" +node.getClass().getName() + JSONObject.toJSONString(node));

            //assign global id to node: tid - local_event_number (consistently!)
            //unique
            node.gid = Bytes.longs.add(tid, seq.numOfEvents);
            if (node instanceof TBeginNode) {
              NewReachEngine.saveToThreadFirstNode(tid, (TBeginNode) node);
            } else if (node instanceof TEndNode) {
              NewReachEngine.saveToThreadLastNode(tid, (TEndNode) node);
            } else if (node instanceof TStartNode) {
              NewReachEngine.saveToStartNodeList((TStartNode) node);
            } else if (node instanceof TJoinNode) {
              NewReachEngine.saveToJoinNodeList((TJoinNode) node);
            } else if (node instanceof WaitNode) {
              seq.stat.c_isync++;
              NewReachEngine.saveToWaitNotifyList((IWaitNotifyNode) node);
            } else if (node instanceof NotifyNode) {
              seq.stat.c_isync++;
              NewReachEngine.saveToWaitNotifyList((IWaitNotifyNode) node);
            } else if (node instanceof NotifyAllNode) {
              seq.stat.c_isync++;
              NewReachEngine.saveToWaitNotifyList((IWaitNotifyNode) node);
            }
          }
          bnext = br.read();
        }
      } catch (IOException e) {
        e.printStackTrace();
        //TODO: handle last thread node once error happens
        TEndNode node = new TEndNode(tid, tid, 0);//failed
        seq.numOfEvents++;
        node.gid = Bytes.longs.add(tid, seq.numOfEvents);

        NewReachEngine.saveToThreadLastNode(tid, node);

      }
      br.finish(fileInfo);
    } catch (Exception e) {
      LOG.error("error parsing trace " + tid, e);
      seq.events = null;
      return seq;
    }
    return seq;
  }

  private static TLHeader getHeader(final int typeIdx,
                                    ByteReader breader) throws IOException {
    if (typeIdx != 13)
      throw new RuntimeException("Could not read header");
//        const u64 version = UFO_VERSION;
//        const TidType tid;
//        const u64 timestamp;
//        const u32 length;
    long version = getLong64b(breader);
    short tidParent = getShort(breader);
    long time = getLong64b(breader);
    int len = getInt(breader);
    //LOG.debug(">>> UFO header version:{}  tid:{}  time:{}  len:{}", version , tidParent, new Date(time), len);
    return new TLHeader(tidParent, version, time, len);
  }

  private AbstractNode getNode(final short curTid,
                               final int typeIdx,
                               ByteReader breader, Session s) throws IOException {
    short tidParent;
    short tidKid;
    long addr;
    long pc;
    int size;
    long time;
    int eTime;
    long len;

    int type_idx__ = typeIdx & 0xffffff3f;
    
    switch (typeIdx) {
    		
      case 0: // cBegin
        tidParent = getShort(breader);
        pc = getLong48b(breader);
        eTime = getInt(breader);
        long tmp = getLong48b(breader);
        tmp = getInt(breader);
        tmp = getLong48b(breader);
        tmp = getInt(breader);
        return new TBeginNode(curTid, tidParent, eTime);
      case 1: // cEnd
        tidParent = getShort(breader);
        eTime = getInt(breader);
        return new TEndNode(curTid, tidParent, eTime);
      case 2: // thread start
          long index = getLong48b(breader);
        tidKid = getShort(breader);
        eTime = getInt(breader);
        pc = getLong48b(breader);
        TLEventSeq.stat.c_tstart++;
        return new TStartNode(index,curTid, tidKid, eTime, pc);
      case 3: // join
        index = getLong48b(breader);
        tidKid = getShort(breader);
        eTime = getInt(breader);
        pc = getLong48b(breader);
        TLEventSeq.stat.c_join++;
        return new TJoinNode(index,curTid, tidKid, eTime, pc);
      case 4: // lock  8 + (13 + 48 -> 64) -> 72
        //long index = getLong48b(breader);
        addr = getLong48b(breader);
        pc = getLong48b(breader);
        TLEventSeq.stat.c_lock++;
        //lastIdx = index;
        System.out.println("threadId"+ curTid + "lock");
        return null;//JEFF
      case 5: // nUnlock
        addr = getLong48b(breader);
        pc = getLong48b(breader);
        TLEventSeq.stat.c_unlock++;
        System.out.println("threadId"+ curTid + "c_unlock");
        return null;//JEFF
      case 6: // alloc
//        index = getLong48b(breader);
        addr = getLong48b(breader);
        pc = getLong48b(breader);
        size = getInt(breader);
        TLEventSeq.stat.c_alloc++;
        ArrayList<AbstractNode> nodes= new ArrayList<AbstractNode>();
        nodes.add(new AllocNode(curTid, pc, addr, size));
        //System.out.println("threadId"+ curTid + "alloc"+ "==="+   s.addr2line.sourceInfo(nodes).values().toString());

        // lastIdx = index;
        return null;//JEFF
      case 7: // dealloc
//        index = getLong48b(breader);
        addr = getLong48b(breader);
        pc = getLong48b(breader);
        size = getInt(breader);
        TLEventSeq.stat.c_dealloc++;
        //lastIdx = index;
        ArrayList<AbstractNode> nodes1= new ArrayList<AbstractNode>();
        nodes1.add(new DeallocNode(curTid, pc, addr, size));
        //System.out.println("threadId"+ curTid + "c_dealloc"+ "==="+   s.addr2line.sourceInfo(nodes1).values().toString());
        return null;//JEFF
      case 10: // range r
//        index = getLong48b(breader);
        addr = getLong48b(breader);
        pc = getLong48b(breader);
        size = getInt(breader);
        TLEventSeq.stat.c_range_r++;
        //lastIdx = index;
        ArrayList<AbstractNode> nodes2= new ArrayList<AbstractNode>();
        nodes2.add(new RangeReadNode(curTid, pc, addr, size));
        System.out.println("threadId"+ curTid + "=====RRRR" + "=======" + s.addr2line.sourceInfo(nodes2).values().toString());
        return null;//JEFF
      case 11: // range w
//        index = getLong48b(breader);
        addr = getLong48b(breader);
        pc = getLong48b(breader);
        size = getInt(breader);
        TLEventSeq.stat.c_range_w++;
        System.out.println("threadId"+ curTid + "write");
        return null;//JEFF
      case 12: // PtrAssignment
        long src = getLong48b(breader);
        long dest = getLong48b(breader);
        System.out.println("threadId"+ curTid + "PtrAssignment");
        //long idx = lastIdx;
        //lastIdx = 0;
//  public PtrPropNode(short tid, long src, long dest, long idx) {
        return null;//JEFF
      case 14: // InfoPacket
//        const u64 timestamp;
//        const u64 length;
        time = getLong64b(breader);
        len = getLong64b(breader);
//        LOG.debug(">>> UFO packet:{}  time: {} len: {} ", new Date(time), len);
        System.out.println("threadId"+ curTid + "InfoPacket");
        return null;
      case 15: //Func Entry
        pc = getLong48b(breader);
        //FuncEntryNode funcEntryNode = new FuncEntryNode(curTid, pc);
        //JEFF
        //LOG.debug(funcEntryNode.toString());
        FuncEntryNode funcEntryNode = new FuncEntryNode(curTid, pc);
        ArrayList<AbstractNode> nodes5= new ArrayList<AbstractNode>();
        nodes5.add(funcEntryNode);
        LongArrayList pcLs = new LongArrayList(nodes5.size());
        pcLs.add(funcEntryNode.pc);
        //System.out.println("threadId"+ curTid + "=====Func Entry" + "=======" + s.addr2line.sourceInfo(pcLs).values().toString());
        return null;//JEFF
      case 16: //Func Exit
        //System.out.println("threadId"+ curTid + "Func Exit");

        return null;//JEFF
      case 17: // ThrCondWait
         index = getLong48b(breader);
    	  	long   cond = getLong48b(breader);
    	  	long  mutex = getLong48b(breader);
        pc = getLong48b(breader);
        TLEventSeq.stat.c_wait++;
    	    return new WaitNode(index,curTid,cond,mutex,pc); 
      case 18: // ThrCondSignal
    	  	index = getLong48b(breader);
    	  	cond = getLong48b(breader);
             pc = getLong48b(breader);
             TLEventSeq.stat.c_notify++;
       	    return new NotifyNode(index,curTid,cond,pc); 
      case 19: // ThrCondBroadCast
    	  index = getLong48b(breader);
    	  cond = getLong48b(breader);
         pc = getLong48b(breader);
         TLEventSeq.stat.c_notifyAll++;
   	    return new NotifyAllNode(index,curTid,cond,pc); 
      case 20: // de-ref
        long ptrAddr = getLong48b(breader);
        System.out.println("threadId"+ curTid + "de-ref");
//        System.out.println(">>> deref " + Long.toHexString(ptrAddr));
        return null;
      default: // 8 + (13 + 48 -> 64) -> 72 + header (1, 2, 4, 8)
        int type_idx = typeIdx & 0xffffff3f;
        if (type_idx <= 13) {
          size = 1 << (typeIdx >> 6);
          MemAccNode accN = null;
          boolean readFlag = false;
          if (type_idx == 8)
          {
            readFlag = true;
            accN = getRWNode(size, false, curTid, breader);
          }
          else if (type_idx == 9)
          {
            accN = getRWNode(size, true, curTid, breader);
          }
          ArrayList<AbstractNode> nodes4= new ArrayList<AbstractNode>();
          nodes4.add(accN);
          System.out.println("threadId"+ curTid + "=====" + (readFlag? "R" : "W") +"=======" + s.addr2line.sourceInfo(nodes4).values().toString());
          //lastIdx = accN.idx;
          return null;//JEFF
        }
        return null;
    }
  }

  private static MemAccNode getRWNode(int size,
                                      boolean isW,
                                      short curTid,
                                      ByteReader breader) throws IOException {

    ByteBuffer valueBuf = ByteBuffer.wrap(new byte[8]).order(ByteOrder.LITTLE_ENDIAN);
//    long index = getLong48b(breader);
    long addr = getLong48b(breader);
    long pc = getLong48b(breader);

    int sz = 0;
    while (sz != size) {
      int v = breader.read();
      valueBuf.put((byte) v);
      sz++;
    }
    long[] st = TLEventSeq.stat.c_read;
    if (isW)
      st = TLEventSeq.stat.c_write;
    Number obj = null;
    switch (size) {
      case 1:
        st[0]++;
        obj = valueBuf.get(0);
        break;
      case 2:
        st[1]++;
        obj = valueBuf.getShort(0);
        break;
      case 4:
        st[2]++;
        obj = valueBuf.getInt(0);
        break;
      case 8:
        st[3]++;
        obj = valueBuf.getLong(0);
        break;
    }
    valueBuf.clear();
    if (isW) {
      return new WriteNode(curTid, pc, addr, (byte) size, obj.longValue());
    } else { // type_idx 9
      return new ReadNode(curTid, pc, addr, (byte) size, obj.longValue());
    }
  }

  public static short getShort(ByteReader breader) throws IOException {
    byte b1 = (byte) breader.read();
    byte b2 = (byte) breader.read();
    return Bytes.shorts.add(b2, b1);
  }

  public static int getInt(ByteReader breader) throws IOException {

    byte b1 = (byte) breader.read();
    byte b2 = (byte) breader.read();
    byte b3 = (byte) breader.read();
    byte b4 = (byte) breader.read();
    return Bytes.ints._Ladd(b4, b3, b2, b1);
//    ints.add(getShort(breader), getShort(breader))
  }


  public static long getLong48b(ByteReader breader) throws IOException {
    byte b0 = (byte) breader.read();
    byte b1 = (byte) breader.read();
    byte b2 = (byte) breader.read();
    byte b3 = (byte) breader.read();
    byte b4 = (byte) breader.read();
    byte b5 = (byte) breader.read();
    return Bytes.longs._Ladd((byte) 0x00, (byte) 0x00, b5, b4, b3, b2, b1, b0);
  }

  public static long getLong64b(ByteReader breader) throws IOException {
    byte b0 = (byte) breader.read();
    byte b1 = (byte) breader.read();
    byte b2 = (byte) breader.read();
    byte b3 = (byte) breader.read();
    byte b4 = (byte) breader.read();
    byte b5 = (byte) breader.read();
    byte b6 = (byte) breader.read();
    byte b7 = (byte) breader.read();
    return Bytes.longs._Ladd(b7, b6, b5, b4, b3, b2, b1, b0);
  }
}
