package com.routeonomy.app.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import ai.pleos.playground.tts.constant.ErrorType;
import ai.pleos.playground.tts.constant.Mode;
import ai.pleos.playground.tts.listener.EventListener;
import ai.pleos.playground.tts.listener.OnInitializeListener;
import java.util.Locale;
import com.routeonomy.app.R;
import com.routeonomy.app.data.AssetRouteRepository;
import com.routeonomy.app.data.AssetSegmentRepository;
import com.routeonomy.app.domain.BriefingPlan;
import com.routeonomy.app.domain.BriefingPlanner;
import com.routeonomy.app.domain.BriefingSegment;
import com.routeonomy.app.domain.DriveSession;
import com.routeonomy.app.domain.GeoPoint;
import com.routeonomy.app.domain.Place;
import com.routeonomy.app.domain.Region;
import com.routeonomy.app.domain.RegionPrediction;
import com.routeonomy.app.domain.RegionPredictor;
import com.routeonomy.app.domain.RegionRoutingKt;
import com.routeonomy.app.domain.RegionTransition;
import com.routeonomy.app.domain.TripContext;
import com.routeonomy.app.domain.TodayDiscoveries;
import com.routeonomy.app.pleos.GearMonitor;
import com.routeonomy.app.pleos.NaviEtaClient;
import com.routeonomy.app.pleos.NaviRouteProbe;
import com.routeonomy.app.pleos.NaviVoiceMonitor;
import com.routeonomy.app.simulation.FakeRouteInput;
import com.routeonomy.app.simulation.FakeVehicleInput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private final DriveSession session = new DriveSession();
    private final BriefingPlanner planner = new BriefingPlanner();
    private List<BriefingSegment> segments;
    private BriefingPlan plan;
    private final Set<String> playedSegmentIds = new HashSet<>();
    private final Set<String> completedSegmentIds = new LinkedHashSet<>();
    private FakeVehicleInput fake;
    private GearMonitor gearMonitor;
    private NaviEtaClient naviEtaClient;
    private NaviRouteProbe naviProbe; // AVD experiment: real navi route/events
    private TextView naviProbeStatus;
    private NaviVoiceMonitor naviVoiceMonitor; // AVD experiment: is navi voice guidance observable?
    private TextView mode, eta, routeStatus, topic, details, quizStatus, sdkStatus, playbackStatus, discoveries;
    private Button quizButton;
    private LinearLayout simulationPanel, playbackControls;
    private Button debugToggleButton;
    private Button dashboardBackButton;
    private PreDriveDashboard dashboard;
    private ScrollView dashboardScroll;
    private ScrollView briefingScroll;
    private boolean briefingStarted;
    private boolean toolsOpen;
    private boolean playbackPaused;
    private ai.pleos.playground.tts.TextToSpeech tts;
    private boolean ttsReady;
    private boolean playbackActive;
    private List<String> currentChunks = java.util.Collections.emptyList();
    private int chunkIndex;
    private String currentSegmentId;
    private Button pauseButton;
    // Route-aware briefing (F2/F3/F5): A section before a region, B section after entering it.
    private final RegionTransition regionTransition = new RegionTransition();
    private final Set<String> regionsWithContent = new HashSet<>();
    private List<Region> regions;
    private TripContext trip;
    private FakeRouteInput fakeRoute;
    private GeoPoint currentLocation;
    private RegionPrediction prediction;
    private String regionSectionId;
    private String pendingAnnouncement;
    private boolean waitingForRegion;
    private boolean simulationOpen;
    private String preferredFirstId;
    private LinearLayout demoMovePanel;
    private Button enterButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        fake = new FakeVehicleInput(session);
        segments = new AssetSegmentRepository(this).load();
        for (BriefingSegment s : segments) if (s.getRegionId() != null) regionsWithContent.add(s.getRegionId());
        AssetRouteRepository routes = new AssetRouteRepository(this);
        regions = routes.regions();
        trip = routes.demoTrip();
        fakeRoute = new FakeRouteInput(trip, routes.simulationStops());
        currentLocation = trip.getCurrent().getLocation();
        List<String> startRegions = new ArrayList<>();
        for (Region r : regions) if (RegionRoutingKt.contains(r, currentLocation)) startRegions.add(r.getId());
        regionTransition.ignoreRegionsAtStart(startRegions);
        session.setEtaMinutes(trip.getRemainingMinutes());
        updatePrediction();
        replan();
        buildUi();
        render();
        tts = new ai.pleos.playground.tts.TextToSpeech(getApplicationContext(), Mode.ON_DEVICE);
        tts.initialize(new OnInitializeListener() {
            @Override public void onSuccess() {
                tts.addEventListener(new EventListener() {
                    @Override public void onStart() { }
                    @Override public void onReady() { }
                    @Override public void onDone() { runOnUiThread(MainActivity.this::onChunkDone); }
                    @Override public void onError(ErrorType error) {
                        runOnUiThread(() -> {
                            playbackActive = false;
                            playbackStatus.setText("Pleos TTS 오류 · " + error);
                            pauseButton.setText("재생");
                        });
                    }
                    @Override public void onStop() { }
                    @Override public void onUpdatedRms(double rms) { }
                });
                ttsReady = true;
                tts.setLocale(Locale.KOREA);
                runOnUiThread(() -> {
                    playbackStatus.setText("Pleos 차량 TTS 준비 완료");
                    dashboard.showStatus("");
                    render();
                });
            }
            @Override public void onFailure(ErrorType error) {
                runOnUiThread(() -> {
                    String message = "Pleos TTS 초기화 실패 · " + error;
                    playbackStatus.setText(message);
                    dashboard.showStatus(message);
                });
            }
        });
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!ttsReady && "TTS 초기화 중".contentEquals(playbackStatus.getText())) {
                String message = "Pleos 차량 TTS 서비스에 연결되지 않았습니다.";
                playbackStatus.setText(message);
                dashboard.showStatus(message);
            }
        }, 5000);
        gearMonitor = new GearMonitor(this, gear -> { session.setGear(gear); runOnUiThread(this::render); }, message -> runOnUiThread(() -> sdkStatus.setText(message)));
        naviEtaClient = new NaviEtaClient(this,
            (rawTime, meters) -> { runOnUiThread(() -> sdkStatus.setText("NaviHelper 응답 · remainTime(raw)=" + rawTime + " · remainDistance=" + meters + "m")); return kotlin.Unit.INSTANCE; },
            message -> { runOnUiThread(() -> sdkStatus.setText(message)); return kotlin.Unit.INSTANCE; });
        naviEtaClient.start();
        naviProbe = new NaviRouteProbe(this, text -> { runOnUiThread(() -> naviProbeStatus.setText(text)); return kotlin.Unit.INSTANCE; });
        naviVoiceMonitor = new NaviVoiceMonitor(this, (naviSpeaking, usages) -> {
            naviProbe.note((naviSpeaking ? "♪ 내비 음성 재생 중" : "오디오") + " · 재생 중인 소리: " + usages);
            return kotlin.Unit.INSTANCE;
        });
        naviVoiceMonitor.start();
    }

    private void buildUi() {
        int bg=getColor(R.color.routeonomy_background), ink=getColor(R.color.routeonomy_text);
        briefingScroll = new ScrollView(this);
        briefingScroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(28,24,28,24); root.setBackgroundColor(bg); briefingScroll.addView(root);
        dashboardBackButton = button("주행 전 화면");
        dashboardBackButton.setOnClickListener(v -> {
            toolsOpen = false;
            render();
        });
        root.addView(dashboardBackButton, full());
        mode=label(16,ink); root.addView(mode);
        eta=label(64,ink); eta.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(eta);
        routeStatus=label(18,ink); root.addView(routeStatus);
        topic=label(30,ink); topic.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(topic);
        details=label(18,ink); root.addView(details);
        discoveries=label(16,ink); root.addView(discoveries);
        playbackStatus=label(16,ink); playbackStatus.setText("브리핑 대기"); root.addView(playbackStatus);
        playbackControls=new LinearLayout(this);
        pauseButton=button("재생"); pauseButton.setOnClickListener(v->{
            if (!ttsReady) { playbackStatus.setText("Pleos TTS 준비 중"); return; }
            if (playbackPaused) { playbackPaused=false; speakCurrentChunk(); }
            else if (playbackActive) { playbackPaused=true; playbackActive=false; tts.stop(); playbackStatus.setText("일시정지됨 · 현재 문장 처음부터 재개"); pauseButton.setText("재개"); }
            else speakCurrent();
        }); playbackControls.addView(pauseButton,weight());
        Button next=button("다음 주제"); next.setOnClickListener(v->advanceBriefing(false)); playbackControls.addView(next,weight()); root.addView(playbackControls);

        debugToggleButton=button("시뮬레이션 입력 열기");
        simulationPanel=new LinearLayout(this); simulationPanel.setOrientation(LinearLayout.VERTICAL);
        debugToggleButton.setOnClickListener(v->{simulationOpen=!simulationOpen;debugToggleButton.setText(simulationOpen?"시뮬레이션 입력 닫기":"시뮬레이션 입력 열기");render();});
        root.addView(debugToggleButton,full());
        LinearLayout gears=new LinearLayout(this);
        addGearButton(gears,"P",DriveSession.Gear.PARK); addGearButton(gears,"R",DriveSession.Gear.REVERSE);
        addGearButton(gears,"N",DriveSession.Gear.NEUTRAL); addGearButton(gears,"D",DriveSession.Gear.DRIVE);
        simulationPanel.addView(gears);
        LinearLayout etaControls=new LinearLayout(this);
        Button less=button("ETA −5분"); less.setOnClickListener(v->{fake.setEta(session.etaMinutes()-5);refreshRoute();}); etaControls.addView(less,weight());
        Button more=button("ETA +5분"); more.setOnClickListener(v->{fake.setEta(session.etaMinutes()+5);refreshRoute();}); etaControls.addView(more,weight()); simulationPanel.addView(etaControls);
        Button arrived=button("시뮬레이션 도착 토글"); arrived.setOnClickListener(v->{fake.markArrived(session.arrival()!=DriveSession.Arrival.ARRIVED);render();}); simulationPanel.addView(arrived,full());
        // Demo-only movement row. It is the one simulation control kept on the driving screen.
        demoMovePanel=new LinearLayout(this); demoMovePanel.setOrientation(LinearLayout.VERTICAL);
        List<Place> stops = fakeRoute.stops();
        if (!stops.isEmpty()) addMoveButton(stops.get(0));
        enterButton=button("지역 진입"); enterButton.setOnClickListener(v->{Region target=entryTarget(); if(target!=null) onLocationChanged(fakeRoute.enterRegion(target));}); demoMovePanel.addView(enterButton,full());
        if (stops.size() > 1) addMoveButton(stops.get(stops.size()-1));
        Button etaRefresh=button("NaviHelper ETA 조회"); etaRefresh.setOnClickListener(v->naviEtaClient.refresh()); simulationPanel.addView(etaRefresh,full());
        sdkStatus=label(13,ink); simulationPanel.addView(sdkStatus);
        // AVD experiment: does the Pleos navi accept our route and send route/location/arrival events?
        Place destination = trip.getDestination();
        Button probeRoute=button("[실험] 내비 경로 요청 → " + destination.getName());
        probeRoute.setOnClickListener(v->naviProbe.requestRoute(destination.getLocation().getLatitude(), destination.getLocation().getLongitude(), destination.getName()));
        simulationPanel.addView(probeRoute,full());
        LinearLayout probeRow=new LinearLayout(this);
        Button probeQuery=button("[실험] 내비 상태 조회"); probeQuery.setOnClickListener(v->naviProbe.queryState()); probeRow.addView(probeQuery,weight());
        Button probeCancel=button("[실험] 경로 취소"); probeCancel.setOnClickListener(v->naviProbe.cancelRoute()); probeRow.addView(probeCancel,weight());
        simulationPanel.addView(probeRow);
        naviProbeStatus=label(13,ink); naviProbeStatus.setText("내비 실험 대기"); simulationPanel.addView(naviProbeStatus);
        simulationPanel.setVisibility(View.GONE); root.addView(simulationPanel);
        demoMovePanel.setVisibility(View.GONE); root.addView(demoMovePanel);

        quizStatus=label(18,ink); root.addView(quizStatus);
        quizButton=button("도착 후 퀴즈 시작"); quizButton.setOnClickListener(v->quizStatus.setText("시연용 퀴즈 화면 · 문항/정답은 후속 구현")); root.addView(quizButton,full());
        dashboard = new PreDriveDashboard(this, this::startDashboardBriefing,
                this::changeDashboardTopic, () -> openTools(true), () -> openTools(false));
        dashboardScroll = new ScrollView(this);
        dashboardScroll.setFillViewport(true);
        dashboardScroll.addView(dashboard);

        FrameLayout screen = new FrameLayout(this);
        screen.addView(dashboardScroll, new FrameLayout.LayoutParams(-1, -1));
        screen.addView(briefingScroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
    }

    private void openTools(boolean showSimulation) {
        toolsOpen = true;
        simulationOpen = showSimulation;
        debugToggleButton.setText(showSimulation ? "시뮬레이션 입력 닫기" : "시뮬레이션 입력 열기");
        render();
    }

    private void startDashboardBriefing() {
        if (!ttsReady) {
            dashboard.showStatus(playbackStatus.getText());
            return;
        }
        speakCurrent();
    }

    private boolean canChangeDashboardTopic() {
        int topicCount = 0;
        for (BriefingSegment segment : plan.getSegments()) {
            if (segment.getType() == BriefingSegment.Type.MAIN && ++topicCount > 1) return true;
        }
        return false;
    }

    private void changeDashboardTopic() {
        if (!canChangeDashboardTopic()) return;
        List<BriefingSegment> reordered = new ArrayList<>(plan.getSegments());
        BriefingSegment first = reordered.remove(0);
        int nextNonTopic = 0;
        while (nextNonTopic < reordered.size()
                && reordered.get(nextNonTopic).getType() == BriefingSegment.Type.MAIN) {
            nextNonTopic++;
        }
        reordered.add(nextNonTopic, first);
        plan = new BriefingPlan(reordered);
        preferredFirstId = reordered.get(0).getId();
        render();
    }

    private void addMoveButton(Place stop){Button b=button(stop.getName()+"(으)로 이동");b.setOnClickListener(v->onLocationChanged(fakeRoute.moveTo(stop)));demoMovePanel.addView(b,full());}
    /** The demo can only enter the region the trip is predicted to pass; nothing once a region was entered. */
    private Region entryTarget(){
        if (regionTransition.enteredRegionId() != null || prediction == null) return null;
        return prediction.getRegion();
    }
    private String regionName(String id){for(Region r:regions) if(r.getId().equals(id)) return r.getName(); return id;}

    /** Simulated location update; a Pleos FusedLocation/ProximityAlert adapter would call the same path. */
    private void onLocationChanged(int remainingMinutes) {
        currentLocation = fakeRoute.getLocation();
        fake.setEta(remainingMinutes);
        Region entered = RegionPredictor.regionAt(currentLocation, regions);
        if (entered != null && regionTransition.onRegionEntered(entered.getId()) && !playbackActive && !playbackPaused) {
            // Nothing is playing (not started, waiting for the region, or finished): switch now.
            regionTransition.consumePendingSwitch();
            startRegionSection(entered.getId());
            replan();
            render();
            if (briefingStarted && ttsReady && !plan.getSegments().isEmpty()) speakCurrent();
            return;
        }
        refreshRoute();
    }
    /** Recomputes the prediction and queue after a position or remaining-time change. */
    private void refreshRoute() {
        updatePrediction();
        replan();
        if (waitingForRegion && !playbackActive && !playbackPaused && (prediction == null || !plan.getSegments().isEmpty())) {
            // Passed the expected region, or the new estimate leaves room for more A segments: resume.
            waitingForRegion = false;
            render();
            if (briefingStarted && ttsReady && !plan.getSegments().isEmpty()) speakCurrent();
            return;
        }
        render();
    }
    private void updatePrediction() {
        if (regionTransition.enteredRegionId() != null) { prediction = null; return; }
        prediction = RegionPredictor.predict(currentLocation, trip.getDestination().getLocation(), session.etaMinutes(), regions, regionsWithContent);
    }
    private void startRegionSection(String regionId) {
        regionSectionId = regionId;
        prediction = null;
        waitingForRegion = false;
        pendingAnnouncement = "지금 " + regionName(regionId) + "에 들어섰습니다. 이 지역과 연결된 이야기를 들려드릴게요.";
    }
    private String routeStatusText() {
        String dest = "→ " + trip.getDestination().getName();
        if (regionTransition.hasPendingSwitch()) return dest + "\n" + regionName(regionTransition.enteredRegionId()) + " 진입 · 지금 이야기가 끝나면 지역 브리핑으로 넘어가요";
        if (regionSectionId != null) {
            Region here = RegionPredictor.regionAt(currentLocation, regions);
            boolean inside = here != null && here.getId().equals(regionSectionId);
            return dest + "\n" + regionName(regionSectionId) + (inside ? " 구간 · 지역 브리핑" : " 구간 통과");
        }
        if (prediction != null) {
            String name = prediction.getRegion().getName();
            if (waitingForRegion) return dest + "\n" + name + " 진입 대기 · 들어서면 지역 이야기를 이어서 들려드려요";
            return dest + "\n" + name + " 경유 예상 · 약 " + prediction.getEstimatedMinutesToRegion() + "분 뒤 (직선 거리 기준 추정)";
        }
        return dest + "\n경유 예상 지역 없음";
    }
    private void addGearButton(LinearLayout row,String title,DriveSession.Gear gear){Button b=button(title);b.setOnClickListener(v->{fake.setGear(gear);render();});row.addView(b,weight());}
    private TextView label(int size,int color){TextView v=new TextView(this);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,8,0,8);return v;}
    private Button button(String title){Button b=new Button(this);b.setText(title);return b;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private void render(){
        boolean driving = session.isDriving();
        boolean preDrive = !driving && !briefingStarted && !session.canStartQuiz();
        boolean showDashboard = preDrive && !toolsOpen;
        dashboardScroll.setVisibility(showDashboard ? View.VISIBLE : View.GONE);
        briefingScroll.setVisibility(showDashboard ? View.GONE : View.VISIBLE);
        dashboardBackButton.setVisibility(preDrive ? View.VISIBLE : View.GONE);
        mode.setText(driving?"주행 모드":"주차 모드");
        mode.setVisibility(driving?View.GONE:View.VISIBLE);
        BriefingSegment current = plan.getSegments().isEmpty() ? null : plan.getSegments().get(0);
        dashboard.bindTopic(current, canChangeDashboardTopic());
        eta.setText("목적지까지 " + session.etaMinutes()+"분");
        routeStatus.setText(routeStatusText()); topic.setText(current == null ? "브리핑 없음" : current.getTopic());
        details.setText(current == null ? "" : current.getScript() + "\n새 용어: " + current.getTerm().getName() + "\n" + current.getTerm().getExplanation());
        details.setVisibility(driving?View.GONE:View.VISIBLE);
        discoveries.setText(discoveryText());
        discoveries.setVisibility(driving?View.GONE:View.VISIBLE);
        playbackControls.setVisibility(driving?View.GONE:View.VISIBLE);
        debugToggleButton.setVisibility(driving?View.GONE:View.VISIBLE);
        simulationPanel.setVisibility(simulationOpen && !driving ? View.VISIBLE : View.GONE);
        // Demo-only: the movement row stays available in D so the region switch can be shown while driving.
        demoMovePanel.setVisibility(simulationOpen ? View.VISIBLE : View.GONE);
        Region target = entryTarget();
        enterButton.setText((target == null ? "지역" : target.getName()) + " 진입");
        enterButton.setEnabled(target != null);
        quizButton.setVisibility(driving?View.GONE:View.VISIBLE);
        quizStatus.setVisibility(driving?View.GONE:View.VISIBLE);
        quizButton.setEnabled(session.canStartQuiz());
        quizStatus.setText(session.canStartQuiz()?"목적지 도착 확인 · 정차 중 퀴즈 가능":"P단이어도 목적지 도착 전에는 퀴즈를 시작할 수 없습니다.");
        if (!playbackPaused && !playbackActive && !waitingForRegion && playbackStatus != null && !playbackStatus.getText().toString().startsWith("재생 완료") && !playbackStatus.getText().toString().startsWith("Pleos TTS 오류") && !playbackStatus.getText().toString().startsWith("Pleos TTS 초기화 실패")) playbackStatus.setText(ttsReady?"브리핑 재생 준비":"TTS 초기화 중");
        if(pauseButton!=null) pauseButton.setText(playbackPaused?"재개":"재생/일시정지");
    }
    private String discoveryText(){
        List<BriefingSegment> heard = TodayDiscoveries.from(segments, completedSegmentIds);
        if (heard.isEmpty()) return "오늘의 발견\n아직 끝까지 들은 브리핑이 없습니다.";
        StringBuilder text = new StringBuilder("오늘의 발견");
        for (BriefingSegment s : heard) {
            text.append("\n\n• ").append(s.getTopic());
            if (!s.getKeywords().isEmpty()) text.append("\n키워드: ").append(String.join(", ", s.getKeywords()));
            text.append("\n용어: ").append(s.getTerm().getName()).append(" — ").append(s.getTerm().getExplanation());
            text.append("\n원문: ").append(s.getSources().isEmpty() ? "없음" : s.getSources().get(0));
        }
        return text.toString();
    }
    /** Replans unplayed segments for the current section, keeping a playing or paused segment at the head. */
    private void replan(){
        BriefingSegment current = null;
        if (currentSegmentId != null && plan != null && !plan.getSegments().isEmpty() && plan.getSegments().get(0).getId().equals(currentSegmentId)) current = plan.getSegments().get(0);
        Set<String> exclude = new HashSet<>(playedSegmentIds);
        if (current != null) exclude.add(current.getId());
        BriefingPlan next;
        if (regionSectionId != null) next = planner.planRegion(session.etaMinutes(), regionSectionId, segments, exclude);
        else if (prediction != null) next = planner.planBeforeRegion(prediction.getEstimatedMinutesToRegion(), segments, exclude, java.util.Collections.emptySet());
        else next = planner.plan(session.etaMinutes(), segments, exclude, java.util.Collections.emptySet());
        List<BriefingSegment> queue = new ArrayList<>();
        if (current != null) queue.add(current);
        queue.addAll(next.getSegments());
        if (current == null && preferredFirstId != null) {
            // Keep the topic picked on the pre-drive dashboard first until playback starts.
            for (int i = 0; i < queue.size(); i++) if (queue.get(i).getId().equals(preferredFirstId)) { queue.add(0, queue.remove(i)); break; }
        }
        plan = new BriefingPlan(queue);
    }
    private void advanceBriefing(boolean completed){
        if(plan==null||plan.getSegments().isEmpty())return;
        if(tts!=null) tts.stop();
        BriefingSegment previous=plan.getSegments().get(0);
        if (completed) completedSegmentIds.add(previous.getId());
        playedSegmentIds.add(previous.getId());
        playbackActive=false; playbackPaused=false; currentSegmentId=null;
        String switchTo = regionTransition.consumePendingSwitch();
        if (switchTo != null) startRegionSection(switchTo);
        else updatePrediction();
        replan();
        if(!plan.getSegments().isEmpty()) { render(); speakCurrent(); return; }
        if (regionSectionId == null && prediction != null) {
            waitingForRegion = true;
            playbackStatus.setText(prediction.getRegion().getName() + " 진입 대기");
        } else playbackStatus.setText("재생 완료 · " + completedSegmentIds.size() + "개 항목 청취 기록");
        render();
    }
    private void speakCurrent(){
        if(!ttsReady) { playbackStatus.setText("TTS를 사용할 수 없습니다"); return; }
        preferredFirstId=null;
        String switchTo = regionTransition.consumePendingSwitch();
        if (switchTo != null) {
            // Playback stopped (e.g. TTS error) while a switch was pending: drop the interrupted A segment.
            if (currentSegmentId != null) playedSegmentIds.add(currentSegmentId);
            currentSegmentId = null;
            startRegionSection(switchTo);
            replan();
        }
        if(plan==null||plan.getSegments().isEmpty()) {
            if (regionSectionId == null && prediction != null) {
                waitingForRegion = true;
                briefingStarted = true;
                playbackStatus.setText(prediction.getRegion().getName() + " 진입 대기");
                render();
            } else playbackStatus.setText("남은 브리핑이 없습니다");
            return;
        }
        waitingForRegion=false;
        BriefingSegment segment=plan.getSegments().get(0);
        currentSegmentId=segment.getId();
        currentChunks=splitIntoChunks(segment.getScript());
        if (pendingAnnouncement != null) { currentChunks.add(0, pendingAnnouncement); pendingAnnouncement = null; }
        chunkIndex=0;
        speakCurrentChunk();
    }
    private List<String> splitIntoChunks(String script) {
        List<String> chunks = new java.util.ArrayList<>();
        for (String part : script.split("(?<=[.!?。！？])\\s*")) if (!part.trim().isEmpty()) chunks.add(part.trim());
        if (chunks.isEmpty() && script != null && !script.trim().isEmpty()) chunks.add(script.trim());
        return chunks;
    }
    private void speakCurrentChunk() {
        if (currentSegmentId == null || currentChunks.isEmpty() || !ttsReady) return;
        playbackPaused=false; playbackActive=true;
        if (!briefingStarted) {
            briefingStarted = true;
            render();
        }
        BriefingSegment segment=plan.getSegments().get(0);
        playbackStatus.setText("Pleos TTS 재생 중 · "+segment.getTopic());
        pauseButton.setText("일시정지");
        tts.speak(currentChunks.get(chunkIndex), true);
    }
    private void onChunkDone() {
        if (!playbackActive || playbackPaused || currentSegmentId == null) return;
        if (chunkIndex + 1 < currentChunks.size()) { chunkIndex++; speakCurrentChunk(); }
        else advanceBriefing(true);
    }
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.release();}if(gearMonitor!=null)gearMonitor.close();if(naviEtaClient!=null)naviEtaClient.close();if(naviVoiceMonitor!=null)naviVoiceMonitor.close();if(naviProbe!=null)naviProbe.close();super.onDestroy();}
}
