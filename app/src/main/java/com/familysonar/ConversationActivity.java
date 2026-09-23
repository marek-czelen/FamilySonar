package com.familysonar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ConversationActivity extends AppCompatActivity {
    static final String EXTRA_ADDRESS = "com.familysonar.extra.ADDRESS";

    private SmsRepository repository;
    private MessageAdapter adapter;
    private EditText messageInput;
    private TextView attachmentStatus;
    private ImageView attachmentPreview;
    private String address;
    private Uri pendingMediaUri;

    private final ActivityResultLauncher<String[]> mediaPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri == null) {
                            return;
                        }
                        pendingMediaUri = uri;
                        com.bumptech.glide.Glide.with(this)
                                .load(uri)
                                .fitCenter()
                                .into(attachmentPreview);
                        attachmentPreview.setVisibility(View.VISIBLE);
                        attachmentStatus.setText(R.string.attachment_selected);
                        attachmentStatus.setVisibility(View.VISIBLE);
                    });

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadMessages();
        }
    };

    static Intent createIntent(Context context, String address) {
        return new Intent(context, ConversationActivity.class)
                .putExtra(EXTRA_ADDRESS, address);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);
        repository = new SmsRepository(this);
        address = resolveAddress(getIntent());
        if (TextUtils.isEmpty(address)) {
            finish();
            return;
        }

        TextView title = findViewById(R.id.conversationTitle);
        TextView subtitle = findViewById(R.id.conversationSubtitle);
        title.setText(ContactNameResolver.resolve(this, address));
        subtitle.setText(R.string.sms_mode);
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        findViewById(R.id.mmsInfoButton).setOnClickListener(view -> showMmsInfo());

        RecyclerView recyclerView = findViewById(R.id.messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new MessageAdapter(this);
        recyclerView.setAdapter(adapter);

        messageInput = findViewById(R.id.messageInput);
        attachmentStatus = findViewById(R.id.attachmentStatus);
        attachmentPreview = findViewById(R.id.attachmentPreview);
        findViewById(R.id.sendButton).setOnClickListener(view -> sendMessage());
        findViewById(R.id.emojiButton).setOnClickListener(view -> showEmojiKeyboard());
        findViewById(R.id.attachmentButton).setOnClickListener(
                view -> mediaPickerLauncher.launch(new String[]{"image/*"}));
        configureMessageComposerInsets();
        loadMessages();
    }

    private void showEmojiKeyboard() {
        messageInput.requestFocus();
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void configureMessageComposerInsets() {
        View header = findViewById(R.id.conversationHeader);
        final int headerTop = header.getPaddingTop();
        final int headerLeft = header.getPaddingLeft();
        final int headerRight = header.getPaddingRight();
        ViewCompat.setOnApplyWindowInsetsListener(header, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    headerLeft + bars.left,
                    headerTop + bars.top,
                    headerRight + bars.right,
                    view.getPaddingBottom());
            return insets;
        });

        View composer = findViewById(R.id.messageComposer);
        int initialBottomPadding = composer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(composer, (view, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottomInset = Math.max(imeInsets.bottom, systemBarInsets.bottom);
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    initialBottomPadding + bottomInset);
            return insets;
        });
        ViewCompat.requestApplyInsets(composer);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(
                this,
                statusReceiver,
                new IntentFilter(SmsRepository.ACTION_STATUS_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        repository.markConversationRead(address);
        loadMessages();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(statusReceiver);
        super.onStop();
    }

    private String resolveAddress(Intent intent) {
        String extra = intent.getStringExtra(EXTRA_ADDRESS);
        if (!TextUtils.isEmpty(extra)) {
            return extra.trim();
        }
        Uri data = intent.getData();
        if (data == null) {
            return "";
        }
        String recipient = data.getSchemeSpecificPart();
        if (recipient != null && recipient.startsWith("//")) {
            recipient = recipient.substring(2);
        }
        return recipient == null ? "" : Uri.decode(recipient).trim();
    }

    private void loadMessages() {
        List<ChatMessage> messages = repository.loadMessagesForAddress(address);
        adapter.submit(messages);
        RecyclerView recyclerView = findViewById(R.id.messageList);
        if (!messages.isEmpty()) {
            recyclerView.scrollToPosition(messages.size() - 1);
        }
    }

    private void sendMessage() {
        String body = messageInput.getText().toString().trim();
        if (body.isEmpty() && pendingMediaUri == null) {
            return;
        }
        Uri mediaUri = pendingMediaUri;
        messageInput.setText("");
        pendingMediaUri = null;
        attachmentPreview.setImageDrawable(null);
        attachmentPreview.setVisibility(View.GONE);
        attachmentStatus.setText("");
        attachmentStatus.setVisibility(View.GONE);
        repository.sendMessageAsync(address, body, mediaUri, new SmsRepository.SendCallback() {
            @Override
            public void onSuccess() {
                loadMessages();
            }

            @Override
            public void onError(int messageRes) {
                Toast.makeText(ConversationActivity.this, messageRes, Toast.LENGTH_LONG).show();
                loadMessages();
            }
        });
    }

    private void showMmsInfo() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.mms_limited_title)
                .setMessage(R.string.mms_limited_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
