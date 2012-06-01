/*
 * Copyright 2012 frdfsnlght <frdfsnlght@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bennedum.transporter;

import com.x5.template.Chunk;
import com.x5.template.TemplateSet;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bennedum.transporter.Realm.RealmPlayer;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class RealmWebServer {
 
    private int port;
    private int workers;
    private ServerSocket serverSocket;
    private boolean run;
    private Thread serverThread;
    private final List<Worker> workerThreads = new ArrayList<Worker>();
    private File rootFolder;
    private TemplateSet views;
    
    private static final long WORKER_TIMEOUT = 10000;
    private static final String ROOT_FOLDER_NAME = "realm-web-root";
    private static final String VIEW_EXTENSION = "html";
    
    private static final Map<String,String> MIME_TYPES = new HashMap<String,String>();
    
    static {
        MIME_TYPES.put("css", "text/css; charset=utf-8");
        MIME_TYPES.put("txt", "text/plain; charset=utf-8");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
    }
    
    public RealmWebServer(int port, int workers) {
        this.port = port;
        this.workers = workers;
        rootFolder = new File(Global.plugin.getDataFolder(), ROOT_FOLDER_NAME);
        if (Utils.copyFilesFromJar("/resources/realm_web_root/manifest", rootFolder, false))
            Utils.info("installed default realm web root");
    }
    
    public boolean start() {
        try {
            Class.forName("com.x5.template.Chunk");
        } catch (ClassNotFoundException cnfe) {
            Utils.severe("realm web server is unable to start because the Chunk templating system cannot be found!");
            return false;
        }
        if (! rootFolder.isDirectory()) {
            Utils.severe("realm web server is unable to start because the server root '%s' does not exist!", rootFolder.getAbsolutePath());
            return false;
        }
        views = new TemplateSet(rootFolder.getAbsolutePath());
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException ie) {
            Utils.severe(ie, "Error while trying to start realm web server:");
            return false;
        }
        for (int i = 0; i < workers; i++) {
            Worker worker = new Worker();
            Thread w = new Thread(worker, "realm web server worker " + i);
            w.setDaemon(true);
            w.start();
            workerThreads.add(worker);
        }
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket;
                while (run) {
                    try {
                        socket = serverSocket.accept();
                        synchronized (workerThreads) {
                            long expireTime = System.currentTimeMillis() + WORKER_TIMEOUT;
                            while (workerThreads.isEmpty() && (System.currentTimeMillis() < expireTime)) {
                                if (! run) break;
                                try {
                                    workerThreads.wait(WORKER_TIMEOUT);
                                } catch (InterruptedException ie) {}
                            }
                            if (run) {
                                if (System.currentTimeMillis() >= expireTime) {
                                    Utils.warning("realm web server unable to aquire worker to process request!");
                                    socket.close();
                                } else {
                                    Worker worker = workerThreads.remove(0);
                                    Utils.debug("realm web server accepted connection from %s", socket.getInetAddress().toString());
                                    worker.setSocket(socket);
                                }
                            }
                        }
                    } catch (IOException ie) {}
                }
            }
        }, "realm web server listener");
        run = true;
        serverThread.setDaemon(true);
        serverThread.start();
        Utils.info("realm web server started on port %s", port);
        return true;
    }
    
    public void stop() {
        run = false;
        serverThread.interrupt();
        serverThread = null;
        synchronized (workerThreads) {
            for (Worker worker : workerThreads)
                worker.stop();
            workerThreads.clear();
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ioe) {}
            serverSocket = null;
        }
        Utils.info("realm web server stopped");
    }
    
    private final class Worker implements Runnable {
        
        private static final int READ_TIMEOUT = 5000;
        private static final int BUFFER_SIZE = 2048;
        private final byte[] EOL = {(byte)'\r', (byte)'\n'};
        
        private boolean run;
        private Socket socket;
        private byte[] buffer;
        
        private String request;
        private String method;
        private String path;
        private Map<String,String> params = new HashMap<String,String>();
        
        public Worker() {
            buffer = new byte[BUFFER_SIZE];
            socket = null;
        }
        
        @Override
        public void run() {
            run = true;
            while (run) {
                if (socket == null) {
                    synchronized (this) {
                        try {
                            wait();
                        } catch (InterruptedException e) {}
                    }
                }
                if (! run) break;
                try {
                    handleClient();
                } catch (Throwable t) {
                    Utils.severe(t, "realm web server encountered an error while processing a request from %s:", socket.getInetAddress());
                } finally {
                    try {
                        socket.close();
                    } catch (IOException ie) {}
                    socket = null;
                }
                synchronized (workerThreads) {
                    workerThreads.add(this);
                    workerThreads.notify();
                }
            }            
        }
        
        public void stop() {
            run = false;
            synchronized (this) {
                notify();
            }
        }
        
        public void setSocket(Socket socket) {
            this.socket = socket;
            synchronized (this) {
                notify();
            }
        }
        
        private void handleClient() throws IOException {
            InputStream is = null;
            PrintStream ps = null;
            try {
                is = new BufferedInputStream(socket.getInputStream());
                ps = new PrintStream(socket.getOutputStream());
                socket.setSoTimeout(READ_TIMEOUT);
                socket.setTcpNoDelay(true);
                for (int i = 0; i < BUFFER_SIZE; i++) buffer[i] = 0;
                    /* We only support HTTP GET/HEAD, and don't
                    * support any fancy HTTP options,
                    * so we're only interested really in
                    * the first line.
                    */
                    int totalRead = 0, read;
                    while (totalRead < BUFFER_SIZE) {
                        read = is.read(buffer, totalRead, BUFFER_SIZE - totalRead);
                        if (read == -1) {
                            // premature end of input
                            return;
                        }
                        int pos = totalRead;
                        totalRead += read;
                        for (; pos < totalRead; pos++) {
                            if ((buffer[pos] == (byte)'\n') || (buffer[pos] == (byte)'\r')) {
                                // got a complete line
                                try {
                                    request = new String(buffer, 0, totalRead, "ASCII");
                                } catch (UnsupportedEncodingException uee) {
                                    request = "";
                                }
                                processRequest(ps);
                                return;
                            }
                        }
                    }
                    httpError(ps, 414, "Request too long");
            } finally {
                try {
                    if (is != null) is.close();
                    if (ps != null) {
                        ps.flush();
                        ps.close();
                    }
                    socket.close();
                } catch (IOException ie) {}
            }
        }

        private void processRequest(PrintStream ps) throws IOException {
            String r = request;
            int pos = r.indexOf(" ");
            if (pos == -1) {
                httpError(ps, 400, "Bad request");
                return;
            }
            method = r.substring(0, pos).toUpperCase();
            r = r.substring(pos + 1);
            pos = r.indexOf(" ");
            if (pos == -1) pos = r.length();
            r = r.substring(0, pos);
            params.clear();
            if (r.length() == 0)
                path = "/";
            else {
                pos = r.indexOf("?");
                if (pos == -1)
                    path = r;
                else {
                    path = r.substring(0, pos);
                    r = r.substring(pos + 1);
                    pos = path.indexOf("#");
                    if (pos != -1)
                        path = path.substring(0, pos);
                    for (String paramPart : r.split("&")) {
                        pos = paramPart.indexOf("=");
                        String key, value = null;
                        try {
                            if (pos == -1)
                                key = URLDecoder.decode(paramPart, "UTF-8");
                            else {
                                key = URLDecoder.decode(paramPart.substring(0, pos), "UTF-8");
                                value = URLDecoder.decode(paramPart.substring(pos + 1), "UTF-8");
                            }
                            params.put(key, value);
                        } catch (UnsupportedEncodingException uee) {}
                    }
                }
            }
            
            if ((! method.equals("GET")) && (! method.equals("HEAD"))) {
                httpError(ps, 405, "Bad method");
                return;
            }

            // find and load the view
            
            String realPath = path;
            if (realPath.endsWith("/")) realPath += "index";
            if (realPath.startsWith("/")) realPath = realPath.substring(1);
            int extensionPos = realPath.lastIndexOf(".");
            String extension;
            if (extensionPos == -1) {
                extension = VIEW_EXTENSION;
                extensionPos = realPath.length();
                realPath += "." + extension;
            } else
                extension = realPath.substring(extensionPos + 1).toLowerCase();
            realPath = realPath.replace("/", File.separator);
            File realFile = new File(rootFolder, realPath);
            if (! realFile.getParentFile().getAbsolutePath().startsWith(rootFolder.getAbsolutePath())) {
                httpError(ps, 403, "Forbidden");
                return;
            }
            if (! realFile.isFile()) {
                httpError(ps, 404, "Not found");
                return;
            }
            if (! realFile.canRead()) {
                httpError(ps, 403, "Forbidden");
                return;
            }
            
            byte[] bodyContent;
            String bodyContentType;
            
            if (extension.equals(VIEW_EXTENSION)) {
                String viewName = realPath.substring(0, extensionPos);
                // load the view
                Chunk view = views.makeChunk(viewName, extension);
                // add stuff to the view
                view.set("realmName", Realm.getName());
                view.set("playerNames", Realm.getPlayerNames());
                // load a player if a name is provided
                if (params.containsKey("name")) {
                    String searchName = params.get("name").trim();
                    if (searchName.length() > 0) {
                        view.set("searchName", searchName);
                        RealmPlayer player = Realm.getPlayer(searchName);
                        if (player != null)
                            view.addData(player);
                    }
                }
                // render the view
                try {
                    bodyContent = view.toString().getBytes("UTF-8");
                    bodyContentType = "text/html; charset=utf-8";
                } catch (UnsupportedEncodingException uee) {
                    httpError(ps, 500, "Server error: UTF-8 is an unknown encoding");
                    return;
                }
            } else {
                // serving a real file
                FileInputStream is = new FileInputStream(realFile);
                int totalRead = 0, numRead;
                bodyContent = new byte[(int)realFile.length()];
                while (totalRead < bodyContent.length) {
                    numRead = is.read(bodyContent, totalRead, bodyContent.length - totalRead);
                    if (numRead == -1) {
                        httpError(ps, 501, "Internal error: unexpected end of file");
                        return;
                    }
                    totalRead += numRead;
                }
                is.close();
                // figure out content type
                bodyContentType = MIME_TYPES.get(extension);
                if (bodyContentType == null)
                    bodyContentType = "application/octet-stream";
            }
            
            ps.print("HTTP/1.0 200 OK");
            ps.write(EOL);
            ps.print("Server: Transporter Realm Web Server");
            ps.write(EOL);
            ps.print("Date: " + (new Date()));
            ps.write(EOL);
            ps.print("Content-Type: " + bodyContentType);
            ps.write(EOL);
            ps.print("Content-Length: " + bodyContent.length);
            ps.write(EOL);
            ps.print("Last-Modified: " + (new Date(realFile.lastModified())));            
            ps.write(EOL);
            
            if (method.equals("HEAD")) return;
            
            ps.write(EOL);
            ps.write(bodyContent);
        }
        
        private void httpError(PrintStream ps, int status, String message) throws IOException {
            ps.print("HTTP/1.0 ");
            ps.print(status);
            ps.print(" ");
            ps.print(message);
            ps.write(EOL);
            ps.write(EOL);
            ps.print(message);
        }
        
    }
    
}
