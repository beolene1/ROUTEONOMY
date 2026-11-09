package com.routeonomy.app.ui;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.speech.tts.TextToSpeech;
import com.routeonomy.app.R;
import com.routeonomy.app.data.AssetSegmentRepository;
import com.routeonomy.app.domain.BriefingPlan;
import com.routeonomy.app.domain.BriefingPlanner;
import com.routeonomy.app.domain.BriefingSegment;
import com.routeonomy.app.domain.DriveSession;
import com.routeonomy.app.pleos.GearMonitor;
import com.routeonomy.app.pleos.NaviEtaClient;
import com.routeonomy.app.simulation.FakeVehicleInput;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private final DriveSession session = new DriveSession();
    private final BriefingPlanner planner = new BriefingPlanner();
    private List<BriefingSegment> segments;
    private BriefingPlan plan;
    private final Set<String> playedSegmentIds = new HashSet<>();
    private FakeVehicleInput fake;
    private GearMonitor gearMonitor;
    private NaviEtaClient naviEtaClient;
    private TextView mode, eta, topic, details, quizStatus, sdkStatus, playbackStatus;
    private Button quizButton;
    private LinearLayout simulationPanel, playbackControls;
    private Button debugToggleButton;
    private boolean playbackPaused;
    private TextToSpeech tts;
    private boolean ttsReady;
    private int playbackIndex;
    private Button pauseButton;
    private static final int DEMO_ETA_MINUTES = 15;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        fake = new FakeVehicleInput(session);
        segments = new AssetSegmentRepository(this).load();
        replan();
        session.setEtaMinutes(DEMO_ETA_MINUTES);
        replan();
        tts = new TextToSpeech(this, status -> { ttsReady = status == TextToSpeech.SUCCESS; runOnUiThread(this::render); });
        buildUi();
        render();
        gearMonitor = new GearMonitor(this, gear -> { session.setGear(gear); runOnUiThread(this::render); }, message -> runOnUiThread(() -> sdkStatus.setText(message)));
        naviEtaClient = new NaviEtaClient(this,
            (rawTime, meters) -> { runOnUiThread(() -> sdkStatus.setText("NaviHelper 응답 · remainTime(raw)=" + rawTime + " · remainDistance=" + meters + "m")); return kotlin.Unit.INSTANCE; },
            message -> { runOnUiThread(() -> sdkStatus.setText(message)); return kotlin.Unit.INSTANCE; });
        naviEtaClient.start();
    }

    private void buildUi() {
        int bg=getColor(R.color.routeonomy_background), ink=getColor(R.color.routeonomy_text);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(28,24,28,24); root.setBackgroundColor(bg); scroll.addView(root);
        mode=label(16,ink); root.addView(mode);
        eta=label(64,ink); eta.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(eta);
        topic=label(30,ink); topic.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(topic);
        details=label(18,ink); root.addView(details);
        playbackStatus=label(16,ink); playbackStatus.setText("브리핑 대기"); root.addView(playbackStatus);
        playbackControls=new LinearLayout(this);
        pauseButton=button("재생"); pauseButton.setOnClickListener(v->{if(playbackPaused){playbackPaused=false; speakCurrent();}else if(tts!=null&&tts.isSpeaking()){tts.stop();playbackPaused=true;playbackStatus.setText("일시정지됨");pauseButton.setText("재개");}}); playbackControls.addView(pauseButton,weight());
        Button next=button("다음 주제"); next.setOnClickListener(v->advanceBriefing()); playbackControls.addView(next,weight()); root.addView(playbackControls);

        debugToggleButton=button("시뮬레이션 입력 열기");
        simulationPanel=new LinearLayout(this); simulationPanel.setOrientation(LinearLayout.VERTICAL);
        debugToggleButton.setOnClickListener(v->{int visibility=simulationPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE;simulationPanel.setVisibility(visibility);debugToggleButton.setText(visibility==View.VISIBLE?"시뮬레이션 입력 닫기":"시뮬레이션 입력 열기");});
        root.addView(debugToggleButton,full());
        LinearLayout gears=new LinearLayout(this);
        addGearButton(gears,"P",DriveSession.Gear.PARK); addGearButton(gears,"R",DriveSession.Gear.REVERSE);
        addGearButton(gears,"N",DriveSession.Gear.NEUTRAL); addGearButton(gears,"D",DriveSession.Gear.DRIVE);
        simulationPanel.addView(gears);
        LinearLayout etaControls=new LinearLayout(this);
        Button less=button("ETA −5분"); less.setOnClickListener(v->{fake.setEta(session.etaMinutes()-5);replan();render();}); etaControls.addView(less,weight());
        Button more=button("ETA +5분"); more.setOnClickListener(v->{fake.setEta(session.etaMinutes()+5);replan();render();}); etaControls.addView(more,weight()); simulationPanel.addView(etaControls);
        Button arrived=button("시뮬레이션 도착 토글"); arrived.setOnClickListener(v->{fake.markArrived(session.arrival()!=DriveSession.Arrival.ARRIVED);render();}); simulationPanel.addView(arrived,full());
        Button etaRefresh=button("NaviHelper ETA 조회"); etaRefresh.setOnClickListener(v->naviEtaClient.refresh()); simulationPanel.addView(etaRefresh,full());
        sdkStatus=label(13,ink); simulationPanel.addView(sdkStatus);
        simulationPanel.setVisibility(View.GONE); root.addView(simulationPanel);

        quizStatus=label(18,ink); root.addView(quizStatus);
        quizButton=button("도착 후 퀴즈 시작"); quizButton.setOnClickListener(v->quizStatus.setText("시연용 퀴즈 화면 · 문항/정답은 후속 구현")); root.addView(quizButton,full());
        setContentView(scroll);
    }

    private void addGearButton(LinearLayout row,String title,DriveSession.Gear gear){Button b=button(title);b.setOnClickListener(v->{fake.setGear(gear);render();});row.addView(b,weight());}
    private TextView label(int size,int color){TextView v=new TextView(this);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,8,0,8);return v;}
    private Button button(String title){Button b=new Button(this);b.setText(title);return b;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private void render(){
        boolean driving=session.isDriving(); mode.setText(driving?"주행 모드":"주차 모드");
        mode.setVisibility(driving?View.GONE:View.VISIBLE);
        BriefingSegment current = plan.getSegments().isEmpty() ? null : plan.getSegments().get(0);
        eta.setText("시연 ETA " + session.etaMinutes()+"분"); topic.setText(current == null ? "브리핑 없음" : current.getTopic());
        details.setText(current == null ? "" : current.getScript() + "\n새 용어: " + current.getTerm().getName() + "\n" + current.getTerm().getExplanation());
        details.setVisibility(driving?View.GONE:View.VISIBLE);
        playbackControls.setVisibility(driving?View.GONE:View.VISIBLE);
        debugToggleButton.setVisibility(driving?View.GONE:View.VISIBLE);
        simulationPanel.setVisibility(driving?View.GONE:simulationPanel.getVisibility());
        quizButton.setVisibility(driving?View.GONE:View.VISIBLE);
        quizStatus.setVisibility(driving?View.GONE:View.VISIBLE);
        quizButton.setEnabled(session.canStartQuiz());
        quizStatus.setText(session.canStartQuiz()?"목적지 도착 확인 · 정차 중 퀴즈 가능":"P단이어도 목적지 도착 전에는 퀴즈를 시작할 수 없습니다.");
        if (!playbackPaused && (tts == null || !tts.isSpeaking()) && playbackStatus != null && !"재생 완료".contentEquals(playbackStatus.getText())) playbackStatus.setText(ttsReady?"브리핑 재생 준비":"TTS 초기화 중");
        if(pauseButton!=null) pauseButton.setText(playbackPaused?"재개":"재생/일시정지");
    }
    private void replan(){plan=planner.plan(session.etaMinutes(),segments,playedSegmentIds,java.util.Collections.emptySet());}
    private void advanceBriefing(){
        if(plan==null||plan.getSegments().isEmpty())return;
        if(tts!=null) tts.stop();
        playedSegmentIds.add(plan.getSegments().get(0).getId());
        playbackIndex++;
        replan();
        render();
        if(!plan.getSegments().isEmpty()) speakCurrent(); else playbackStatus.setText("재생 완료 · " + playedSegmentIds.size() + "개 항목 기록");
    }
    private void speakCurrent(){
        if(!ttsReady||plan==null||plan.getSegments().isEmpty()) { playbackStatus.setText("TTS를 사용할 수 없습니다"); return; }
        BriefingSegment segment=plan.getSegments().get(0);
        playbackStatus.setText("재생 중 · "+segment.getTopic());
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onError(String id){}
            @Override public void onDone(String id){runOnUiThread(()->{if(!plan.getSegments().isEmpty()&&plan.getSegments().get(0).getId().equals(segment.getId()))advanceBriefing();});}
        });
        tts.speak(segment.getScript(),TextToSpeech.QUEUE_FLUSH,null,segment.getId());
    }
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}if(gearMonitor!=null)gearMonitor.close();if(naviEtaClient!=null)naviEtaClient.close();super.onDestroy();}
}
