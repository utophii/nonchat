package com.nonxedy.nonchat.storage;

// loads player data and saves detached snapshots of the current state
public interface SnapshotStorage<T> {
    T load();
    void save(T snapshot);
}