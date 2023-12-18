package aser.ufo.misc;

import trace.MemAccNode;

public class RacePair {
    public MemAccNode firstRaceAccNode1;
    public MemAccNode firstRaceAccNode2;
    public MemAccNode secondRaceAccNode1;
    public MemAccNode secondRaceAccNode2;

    public RacePair() {
    }
     // no                    dependcy
    // firstRaceAccNode1   secondRaceAccNode1
    //secondRaceAccNode2  firstRaceAccNode1
    public RacePair(MemAccNode firstRaceAccNode1, MemAccNode firstRaceAccNode2,
                    MemAccNode secondRaceAccNode1, MemAccNode secondRaceAccNode2) {
        this.firstRaceAccNode1 = firstRaceAccNode1;
        this.firstRaceAccNode2 = firstRaceAccNode2;
        this.secondRaceAccNode1 = secondRaceAccNode1;
        this.secondRaceAccNode2 = secondRaceAccNode2;
    }

    public Pair<MemAccNode, MemAccNode> getFirstRacePair() {
        return new Pair<MemAccNode, MemAccNode>(firstRaceAccNode1, firstRaceAccNode2);
    }

    public Pair<MemAccNode, MemAccNode> getSecondRacePair() {
        return new Pair<MemAccNode, MemAccNode>(secondRaceAccNode1, secondRaceAccNode2);
    }

    public Pair<MemAccNode, MemAccNode> getNoPair() {
        return new Pair<MemAccNode, MemAccNode>(firstRaceAccNode1, secondRaceAccNode2);
    }

    public Pair<MemAccNode, MemAccNode> getDepenPair() {
        return new Pair<MemAccNode, MemAccNode>(secondRaceAccNode1, firstRaceAccNode1);
    }

    public MemAccNode getFirstRaceAccNode1() {
        return firstRaceAccNode1;
    }

    public void setFirstRaceAccNode1(MemAccNode firstRaceAccNode1) {
        this.firstRaceAccNode1 = firstRaceAccNode1;
    }

    public MemAccNode getFirstRaceAccNode2() {
        return firstRaceAccNode2;
    }

    public void setFirstRaceAccNode2(MemAccNode firstRaceAccNode2) {
        this.firstRaceAccNode2 = firstRaceAccNode2;
    }

    public MemAccNode getSecondRaceAccNode1() {
        return secondRaceAccNode1;
    }

    public void setSecondRaceAccNode1(MemAccNode secondRaceAccNode1) {
        this.secondRaceAccNode1 = secondRaceAccNode1;
    }

    public MemAccNode getSecondRaceAccNode2() {
        return secondRaceAccNode2;
    }

    public void setSecondRaceAccNode2(MemAccNode secondRaceAccNode2) {
        this.secondRaceAccNode2 = secondRaceAccNode2;
    }
}
