package com.familysonar;

import java.io.Serializable;

public class Contact implements Serializable {
    private String _name;
    private String _phoneNumber;

    public Contact(String name,String phone) {
        _name = name;
        _phoneNumber = phone;
    }

    public String getName() {
        return _name;
    }

    public String getPhone() {
        return _phoneNumber;
    }

}