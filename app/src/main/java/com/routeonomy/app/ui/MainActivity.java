package com.routeonomy.app.ui;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.routeonomy.app.R;
import com.routeonomy.app.domain.DriveSession;
import com.routeonomy.app.simulation.FakeVehicleInput;

public final class MainActivity extends Activity {
    private final DriveSession session = new DriveSession();
    private FakeVehicleInput fake;
    private TextView mode, eta, topic, details, quizStatus;
    private Button gearButton, arrivedButton, quizButton;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); fake = new FakeVehicleInput(session); buildUi(); render();
    }
    private void buildUi() {
        int bg=getColor(R.color.routeonomy_background), ink=getColor(R.color.routeonomy_text);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(28,24,28,24); root.setBackgroundColor(bg);
        mode=label(16,ink); root.addView(mode);
        eta=label(64,ink); eta.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(eta);
        topic=label(28,ink); topic.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(topic);
        details=label(16,ink); root.addView(details);
        LinearLayout row=new LinearLayout(this);
        Button less=button("ETA −"); less.setOnClickListener(v->{fake.setEta(session.etaMinutes()-5);render();}); row.addView(less,weight());
        Button more=button("ETA +"); more.setOnClickListener(v->{fake.setEta(session.etaMinutes()+5);render();}); row.addView(more,weight()); root.addView(row);
        gearButton=button("기어 변경"); gearButton.setOnClickListener(v->{fake.setGear(session.isDriving()?DriveSession.Gear.PARK:DriveSession.Gear.DRIVE);render();}); root.addView(gearButton,full());
        arrivedButton=button("시뮬레이션: 목적지 도착"); arrivedButton.setOnClickListener(v->{fake.markArrived(session.arrival()!=DriveSession.Arrival.ARRIVED);render();}); root.addView(arrivedButton,full());
        quizStatus=label(18,ink); root.addView(quizStatus);
        quizButton=button("도착 후 퀴즈 시작"); quizButton.setOnClickListener(v->quizStatus.setText("시연용 퀴즈 화면 · 정답 처리는 다음 단계에서 연결")); root.addView(quizButton,full());
        setContentView(root);
    }
    private TextView label(int size,int color){TextView v=new TextView(this);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,8,0,8);return v;}
    private Button button(String title){Button b=new Button(this);b.setText(title);return b;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private void render(){
        boolean driving=session.isDriving(); mode.setText(driving?"주행 모드 · Fake 입력":"주차 모드 · Fake 입력");
        eta.setText(session.etaMinutes()+"분"); topic.setText(driving?"AI 데이터센터와 전력 수요":"오늘의 발견");
        details.setText(driving?"현재 브리핑 주제":"반도체 · AI 데이터센터 | 새 용어: 전력 밀도\nP단은 도착을 뜻하지 않습니다.");
        details.setVisibility(driving?View.GONE:View.VISIBLE);
        gearButton.setText(driving?"P단으로 전환":"D단으로 전환");
        arrivedButton.setText(session.arrival()==DriveSession.Arrival.ARRIVED?"도착 상태 해제":"시뮬레이션: 목적지 도착");
        quizButton.setEnabled(session.canStartQuiz());
        quizStatus.setText(session.canStartQuiz()?"목적지 도착 확인 · 정차 중 퀴즈 가능":"퀴즈는 P단 + 목적지 도착 후에만 가능");
    }
}
