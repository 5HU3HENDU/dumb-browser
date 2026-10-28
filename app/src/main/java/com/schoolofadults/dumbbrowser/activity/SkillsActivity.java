package com.schoolofadults.dumbbrowser.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.schoolofadults.dumbbrowser.R;
import com.schoolofadults.dumbbrowser.skill.Skill;
import com.schoolofadults.dumbbrowser.skill.SkillSync;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists the skills fetched from the repo. A skill appears here as soon as its
 * md file lands in the repo — no app update needed to add one.
 */
public class SkillsActivity extends AppCompatActivity {

    private ListView listView;
    private TextView statusView;
    private Button retryButton;
    private final List<Skill> skills = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skills);
        listView = findViewById(R.id.skills_list);
        statusView = findViewById(R.id.skills_status);
        retryButton = findViewById(R.id.skills_retry);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Skill skill = skills.get(position);
            Intent intent = new Intent(SkillsActivity.this, SkillRunActivity.class);
            intent.putExtra("skill_id", skill.id);
            startActivity(intent);
        });
        retryButton.setOnClickListener(v -> refresh());
        refresh();
    }

    private void refresh() {
        List<Skill> cached = SkillSync.loadCached(this);
        if (!cached.isEmpty()) show(cached, false);
        statusView.setText(R.string.skill_syncing);
        retryButton.setVisibility(android.view.View.GONE);
        SkillSync.sync(this, new SkillSync.Callback() {
            @Override
            public void onDone(List<Skill> fresh, boolean fromCache) {
                if (!fresh.isEmpty()) show(fresh, fromCache);
                statusView.setText(fromCache
                        ? getString(R.string.skill_offline)
                        : getString(R.string.skill_count, fresh.size()));
            }

            @Override
            public void onError(String message) {
                if (skills.isEmpty()) {
                    statusView.setText(R.string.skill_no_skills);
                    retryButton.setVisibility(android.view.View.VISIBLE);
                } else {
                    statusView.setText(R.string.skill_offline);
                }
            }
        });
    }

    private void show(List<Skill> fresh, boolean fromCache) {
        skills.clear();
        skills.addAll(fresh);
        List<String> titles = new ArrayList<>();
        for (Skill s : fresh) titles.add(s.title);
        adapter.clear();
        adapter.addAll(titles);
        adapter.notifyDataSetChanged();
        if (fromCache) statusView.setText(R.string.skill_offline);
    }
}
