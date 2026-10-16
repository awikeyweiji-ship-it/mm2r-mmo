package com.jeyu.clashgptstarter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Deterministic two-probe latency + jitter ranking; never equates fast with account access. */
public final class SmartNodePolicy {
    private SmartNodePolicy() {}

    public static final class Candidate {
        public final String name;
        public final int firstMs, secondMs;
        public Candidate(String name, int firstMs, int secondMs) {
            this.name = name == null ? "" : name;
            this.firstMs = firstMs;
            this.secondMs = secondMs;
        }
        public double score() {
            return (firstMs + secondMs) * 0.5 + Math.abs(firstMs - secondMs) * 0.6;
        }
    }

    public static boolean isSelectCandidate(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String n=name.trim().toUpperCase(Locale.ROOT);
        return !n.equals("DIRECT") && !n.equals("REJECT") && !n.equals("REJECT-DROP")
            && !n.equals("GLOBAL") && !n.equals("PASS") && !n.equals("COMPATIBLE")
            && !n.equals("BLOCK") && !n.equals("AUTO") && !n.equals("PROXY");
    }

    public static List<Candidate> rank(List<Candidate> nodes) {
        List<Candidate> valid = new ArrayList<>();
        if (nodes == null) return valid;
        for (Candidate c : nodes) {
            if (c != null && isSelectCandidate(c.name) && c.firstMs > 0 && c.secondMs > 0
                    && c.firstMs <= 30000 && c.secondMs <= 30000) {
                valid.add(c);
            }
        }
        Collections.sort(valid, Comparator.comparingDouble(Candidate::score)
                .thenComparing(c -> c.name));
        return valid;
    }
}
