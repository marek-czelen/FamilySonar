package com.familysonar;

import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.telephony.SmsManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder>{
    Context context;
    public ArrayList<Contact> contactList;
    public ContactsAdapter(Context _context, ArrayList<Contact> contactList) {
        this.context = _context;
        this.contactList = contactList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_phone, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contact contact = contactList.get(position);
        String displayName = contact.getName();
        holder.phoneNumber.setText(displayName == null || displayName.trim().isEmpty()
                ? contact.getPhone()
                : displayName + " (" + contact.getPhone() + ")");
        holder.localizationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (contact.getPhone() == null || contact.getPhone().trim().isEmpty()) {
                    Toast.makeText(context, "Brak numeru telefonu.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                        != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Brak uprawnienia do wysyłania SMS-ów.", Toast.LENGTH_LONG).show();
                    return;
                }
                SmsManager.getDefault().sendTextMessage(contact.getPhone(), null, "?loc?", null, null);
                Toast.makeText(context, "Wysłano ?loc? do " + contact.getPhone(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return contactList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView phoneNumber;
        ImageButton localizationButton;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            phoneNumber = itemView.findViewById(R.id.phone_number);
            localizationButton = itemView.findViewById(R.id.sendLocalization);

        }
    }

     public void AddItem(String numer){
        AddItem("", numer);
     }

     public void AddItem(String name, String numer){
        contactList.add(new Contact(name, numer));
        this.notifyDataSetChanged();
     }
    public void RemoveItem(int position){
        contactList.remove(position);
        this.notifyItemRemoved(position);
    }
}