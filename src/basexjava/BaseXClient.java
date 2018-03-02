/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package basexjava;

import static com.oracle.jrockit.jfr.DataType.UTF8;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.nio.charset.*;
import javax.management.Query;

/**
 *
 * @author alu2015059
 */
public class BaseXClient implements Closeable{

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final OutputStream out;
    private final BufferedInputStream in;
    
    private final Socket socket;
    private String info;
    
    public BaseXClient(final String host, final int port, final String username, final String password) throws IOException{
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        in = new BufferedInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        
        String string = (String) receive();
        final String[] response = string.split(":");;
        final String code, nonce;
        if(response.length > 1){
            code = username + ':' + response[0] + ':' + password;
            nonce = response[1];
        } else{
            code = password;
            nonce = response[0];
        }
        
        send(username);
        send(md5(md5(code) + nonce));
        
        if(!ok()) throw new IOException("Acces denied");
    }
    
    public void execute(final String command, final OutputStream output) throws  IOException{
        send(command);
        receive(in, (ByteArrayOutputStream) output);
        info = (String) receive();
        if(!ok()) throw new IOException(info);
    }
    
    public String execute(final String command) throws IOException{
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        execute(command, os);
        return new String(os.toByteArray(), UTF8);
    }
    
    public Query query(final String query) throws IOException{
        return new Query(query);
    }
    
    public void create(final String name, final InputStream input) throws IOException{
        send(8, name, input);
    }
    
    public void add(final String path, final InputStream input) throws IOException{
        send(9, path, input);
    }
    
    public void replace(final String path, final InputStream input) throws IOException{
        send(12, path, input);
    }
    
    public void store(final String path, final InputStream input) throws IOException{
        send(12, path, input);
    }
    
    public String info(){
        return info;
    }
    
    @Override
    public void close() throws IOException {
        send("exit");
        out.flush();
        socket.close();
    }
    
    private boolean ok() throws IOException{
        out.flush();
        return in.read() == 0;
    }

    private Object receive() throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        receive(in, os);
        return new String(os.toByteArray(), UTF8);
        
    }
    
    private void send(final String string) throws IOException{
        out.write((string + '\0').getBytes(UTF8));
    }

    private void receive(BufferedInputStream in, ByteArrayOutputStream os) throws IOException{
        for(int b; (b = in.read()) > 0;){
            os.write(b == 0xFF ? in.read() : b);
        }
    }

    private void send(final int code, final String path, final InputStream input) throws IOException{
        out.write(code);
        send(path);
        send(input);
    }
    
    private void send(final InputStream input) throws IOException{
        final BufferedInputStream bis = new BufferedInputStream(input);
        final BufferedOutputStream bos = new BufferedOutputStream(out);
        for(int b; (b = bis.read()) != -1;){
            if(b == 0x00 || b == 0xFF) bos.write(0xFF);
            bos.write(b);
        }
        bos.write(0);
        bos.flush();
        info = (String) receive();
        if(!ok()) throw  new IOException(info);
    }
    
    private String md5(final String pw){
        final StringBuilder sb = new StringBuilder();
        try{
            final MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(pw.getBytes());
            for(final byte b : md.digest()){
                final String s = Integer.toHexString(b & 0xFF);
                if(s.length() == 1) sb.append('0');
                sb.append(s);
            }
        }catch(final NoSuchAlgorithmException ex){
            ex.printStackTrace();
        }
        return sb.toString();
    }
    
    public class Query implements Closeable {

        private final String id;
        private ArrayList<byte[]> cache;
        private int pos;
        
        Query(final String query) throws IOException{
            id = exec(0, query);
        }
        
        public void bind(final String name, final String value) throws IOException{
            bind(name, value, "");
        }
        
        public void bind(final String name, final String value, final String type) throws IOException{
            cache = null;
            exec(3, id + '\0' + name + '\0' + value + '\0' + type);
        }
        
        public void context(final String value) throws IOException{
            context(value, "");
        }
        
        public void context(final String value, final String type) throws IOException{
            cache = null;
            exec(14, id + '\0' + value + '\0' + type);
        }
        
        public boolean more() throws IOException{
            if(cache == null){
                out.write(4);
                send(id);
                cache = new ArrayList<>();
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                while(in.read() > 0){
                    receive(in, os);
                    cache.add(os.toByteArray());
                    os.reset();
                }
                if(!ok()) throw new IOException((String) receive());
                pos = 0;
            }
            if(pos < cache.size()) return true;
            cache = null;
            return false;
        }
        
        public String next() throws IOException{
            return more() ? new String(cache.set(pos++, null), UTF8) : null;
        }
        
        public String execute() throws IOException{
            return exec(5, id);
        }
        
        public String info() throws IOException{
            return exec(6, id);
        }
        
        public String options() throws IOException{
            return exec(7, id);
        }
        
        @Override
        public void close() throws IOException {
            exec(2, id);
        }
        
        private String exec(final int code, final String arg) throws IOException{
        out.write(code);
        send(arg);
        final String s =  (String) receive();
        if(!ok()) throw new IOException((String) receive());
        return s;
    }
        
    }
    
}
