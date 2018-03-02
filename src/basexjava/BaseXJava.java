/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package basexjava;

import java.io.*;
import java.util.*;

/**
 *
 * @author alu2015059
 */
public final class BaseXJava{

    public static void main(String[] args) throws IOException {
        try(BaseXClient session =  new BaseXClient("localhost", 1984, "root", "")){
            session.execute("create db database");
            System.out.println(session.info());
            
            //define input stream
            InputStream bais = new ByteArrayInputStream("hola".getBytes());
            
            session.add("hola/hola.xml", bais);
            System.out.println(session.info());
            
            //define input stream
            bais = new ByteArrayInputStream("hola2".getBytes());
            
            session.add("hola2.xml", bais);
            System.out.println(session.info());
            
            System.out.println(session.execute("xquery collection('database')"));
            session.execute("drop db database");
        }
    }
    
}
