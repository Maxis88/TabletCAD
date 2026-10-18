package pl.tabletcad.app.storage;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

/** One in-process monitor shared by autosave/recovery and library publication. */
final class ProjectFileLocks {
    private static final ConcurrentHashMap<String,Object> LOCKS=new ConcurrentHashMap<>();
    static Object forFolder(File folder)throws IOException{return LOCKS.computeIfAbsent(folder.getCanonicalPath(),key->new Object());}
}
