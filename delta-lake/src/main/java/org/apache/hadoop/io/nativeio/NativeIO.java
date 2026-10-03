package org.apache.hadoop.io.nativeio;

import java.io.FileDescriptor;
import java.io.IOException;

public class NativeIO {

    public static class Windows {
        public enum AccessRight {
            ACCESS_READ(0x0001),
            ACCESS_WRITE(0x0002),
            ACCESS_EXECUTE(0x0020);

            private final int accessRight;

            AccessRight(int access) {
                this.accessRight = access;
            }

            public int accessRight() {
                return accessRight;
            }
        }

        public static boolean access(String path, AccessRight desiredAccess) throws IOException {
            return true;
        }

        public static FileDescriptor createFile(String path, long desiredAccess, long shareMode, long creationDisposition) throws IOException {
            throw new UnsupportedOperationException();
        }

        public static void setFilePointer(FileDescriptor fd, long distanceToMove, long moveMethod) throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    public static boolean isAvailable() {
        return false;
    }

    public static void link(String src, String dst) throws IOException {
        throw new UnsupportedOperationException();
    }
}
