package com.familysonar;

import static android.content.Context.ALARM_SERVICE;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

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
        holder.phoneNumber.setText(contactList.get(position).getPhone());
        holder.localizationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
                Intent alarmIntent = new Intent(context,AlarmReceiverClass.class);
                alarmIntent.putExtra("from",contactList.get(position).getPhone());
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context,0,alarmIntent,PendingIntent.FLAG_IMMUTABLE);
                long timeInMilis = Calendar.getInstance().getTimeInMillis()+1000;
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,timeInMilis,0,pendingIntent);
                Log.d("FalimySonarApp", "onAlamscheduleOnDemend");
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
        contactList.add(new Contact("", numer));
        this.notifyDataSetChanged();
     }
    public void RemoveItem(int position){
        contactList.remove(position);
        this.notifyItemRemoved(position);
    }
}