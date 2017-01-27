package org.truffleruby.builtins;

public enum SyncMode {
    ARRAY_READ,
    ARRAY_WRITE,
    ARRAY_CHANGE_SIZE,
    ARRAY_CHANGE_STORE,
    NONE
}
