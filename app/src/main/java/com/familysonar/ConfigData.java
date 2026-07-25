package com.familysonar;

import android.content.Context;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;

public class ConfigData {
    private ContactList _contacts = null;
    private Context _context = null;
    private String _configFilePath;
    private String _configFileName;


    public ConfigData(Context context) {
        this._context = context;
        this._contacts = new ContactList();
        this._configFilePath = this._context.getFilesDir().getAbsolutePath();
        this._configFileName = "configdata.dat";

    }

    public void Load() throws IOException, ClassNotFoundException {
        File file = new File(Paths.get(this._configFilePath,this._configFileName).toString());
        file.createNewFile();
        FileInputStream  fIn = new FileInputStream(file);
        ObjectInputStream oIs=null;
        try{
             oIs= new ObjectInputStream(fIn);
        }catch (EOFException e){
            this.Save();
            oIs= new ObjectInputStream(fIn);
        }finally {
            this._contacts= (ContactList) oIs.readObject();
        }
    }

    public void Save() throws IOException, ClassNotFoundException {
        File file = new File(Paths.get(this._configFilePath,this._configFileName).toString());
        file.createNewFile();
        FileOutputStream  fOut = new FileOutputStream(file);
        ObjectOutputStream oOutStream = new ObjectOutputStream(fOut);
        oOutStream.writeObject(this._contacts);
    }

    public ArrayList<Contact> getContactList(){
        return this._contacts.getList();
    }


}
