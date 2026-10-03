package com.rixon.learn.spring.data.deltalake.service;

import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.fs.permission.FsPermission;

import java.io.File;
import java.io.IOException;

public class WindowsLocalFileSystem extends LocalFileSystem {

    public WindowsLocalFileSystem() {
        super(new WindowsRawLocalFileSystem());
    }

    public static class WindowsRawLocalFileSystem extends RawLocalFileSystem {
        @Override
        public void setPermission(Path p, FsPermission permission) throws IOException {
            // No-op on Windows to avoid winutils dependency
        }

        @Override
        public void setOwner(Path p, String username, String groupname) throws IOException {
            // No-op on Windows
        }

        @Override
        public boolean mkOneDirWithMode(Path p, File p2f, FsPermission permission) throws IOException {
            return p2f.mkdir();
        }
    }
}
