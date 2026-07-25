package com.familysonar;

import java.io.Serializable;
import java.util.ArrayList;

public class ContactList implements Serializable {
    private ArrayList<Contact> _contacts;
    public ContactList() {
        this._contacts=new ArrayList<>();
    }
    public ArrayList<Contact> getList(){
        return this._contacts;
    }
}
