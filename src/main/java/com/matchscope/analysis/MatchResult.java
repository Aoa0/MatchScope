package com.matchscope.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public abstract class MatchResult {
    protected final TreeMap<String, String> identical;
    protected final TreeSet<String> added;
    protected final TreeSet<String> deleted;
    protected final Logger logger = LoggerFactory.getLogger(MatchResult.class);

    public MatchResult() {
        this.identical = new TreeMap<>(new ClassMatcher.LengthAlphabeticalComparator());
        this.added = new TreeSet<>(new ClassMatcher.LengthAlphabeticalComparator());
        this.deleted = new TreeSet<>(new ClassMatcher.LengthAlphabeticalComparator());
    }

    public boolean addIdentical(String source, String target) {
        return addToMatchMap(identical, source, target);
    }

    public void updateIdentical(String source, String target) {
        // ToDo: remove the record if target exists already
        this.identical.put(source, target);
    }


    public boolean addToMatchMap(Map<String, String> m, String source, String target) {
        if (m.containsKey(source)) {
            if (m.get(source).equals(target)) {
                return true;
            } else {
                logger.debug("source exist: " + source + " " + target);
                return false;
            }
        } else {
            if (m.containsValue(target)) {
                logger.debug("target exists: " + source + " " + target);
                return false;
            } else {
                m.put(source, target);
                return true;
            }
        }
    }

    public void addDeleted(String s) {
        this.deleted.add(s);
    }

    public void addDeletedAll(Collection<String> c) {
        this.deleted.addAll(c);
    }

    public void addAdded(String s) {
        this.added.add(s);
    }

    public void addAddedAll(Collection<String> c) {
        this.added.addAll(c);
    }

    public TreeSet<String> getAdded() {
        return added;
    }

    public int getAddedSize() {
        return added.size();
    }

    public TreeSet<String> getDeleted() {
        return deleted;
    }

    public int getDeletedSize() {
        return deleted.size();
    }

    public TreeMap<String, String> getIdentical() {
        return identical;
    }


    public abstract Map<String, String> getMatches();

    public abstract int getMatchSize();
}
