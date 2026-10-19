package pl.tabletcad.app;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Writes the stack trace of an uncaught exception to the app's external files folder
 * (Android/data/pl.tabletcad.app/files/tabletcad-crash.txt, readable with adb pull without root)
 * and to the internal files folder, then lets the system handler finish the process.
 */
public final class CrashReport {
    private CrashReport() {}
    public static final String NAME="tabletcad-crash.txt";
    public static void install(Context context,String version) {
        final Context app=context.getApplicationContext();final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)-> {
            try {
                StringWriter text=new StringWriter();PrintWriter out=new PrintWriter(text);
                out.println("TabletCAD "+version+" · "+new java.util.Date()+" · wątek "+thread.getName());error.printStackTrace(out);out.flush();
                byte[] data=text.toString().getBytes(StandardCharsets.UTF_8);
                for(File dir:new File[]{app.getExternalFilesDir(null),app.getFilesDir()})if(dir!=null)try(FileOutputStream file=new FileOutputStream(new File(dir,NAME))){file.write(data);}
            }catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }
    /** Non-fatal error caught by the workspace: appended (last ~64 KB kept) to tabletcad-error.txt. */
    public static void note(Context context,String where,Throwable error) {
        try {
            StringWriter text=new StringWriter();PrintWriter out=new PrintWriter(text);
            out.println("---- "+new java.util.Date()+" · "+where);error.printStackTrace(out);out.flush();
            byte[] data=text.toString().getBytes(StandardCharsets.UTF_8);
            Context app=context.getApplicationContext();
            for(File dir:new File[]{app.getExternalFilesDir(null),app.getFilesDir()})if(dir!=null) {
                File file=new File(dir,"tabletcad-error.txt");if(file.length()>64*1024)file.delete();
                try(FileOutputStream f=new FileOutputStream(file,true)){f.write(data);}
            }
        }catch(Throwable ignored){}
        android.util.Log.e("TabletCAD",where,error);
    }
    /** Text of a report written after the given time, or null. */
    public static String pending(Context context,long seenAt) {
        File file=new File(context.getFilesDir(),NAME);if(!file.isFile()||file.lastModified()<=seenAt)return null;
        try{return new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}catch(IOException e){return null;}
    }
}
