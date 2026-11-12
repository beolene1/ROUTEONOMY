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
import com.routeonomy.app.data.AssetQuizRepository;
import com.routeonomy.app.data.AssetRouteRepository;
import com.routeonomy.app.data.AssetSegmentRepository;
import com.routeonomy.app.domain.BriefingPlan;
import com.routeonomy.app.domain.BriefingPlanner;
import com.routeonomy.app.domain.BriefingSegment;
import com.routeonomy.app.domain.DriveSession;
import com.routeonomy.app.domain.GeoPoint;
import com.routeonomy.app.domain.Geofence;
import com.routeonomy.app.domain.GeofenceGate;
import com.routeonomy.app.domain.Place;
import com.routeonomy.app.domain.QuizQuestion;
import com.routeonomy.app.domain.QuizSession;
import com.routeonomy.app.domain.Region;
import com.routeonomy.app.domain.RegionPrediction;
import com.routeonomy.app.domain.RegionPredictor;
import com.routeonomy.app.domain.RegionTransition;
import com.routeonomy.app.domain.RemainingTimeEstimator;
import com.routeonomy.app.domain.TripContext;
import com.routeonomy.app.domain.TodayDiscoveries;
import com.routeonomy.app.pleos.GearMonitor;
import com.routeonomy.app.pleos.NaviEtaClient;
import com.routeonomy.app.pleos.NaviRouteProbe;
import com.routeonomy.app.pleos.FusedLocationSource;
import com.routeonomy.app.pleos.NaviVoiceMonitor;
import com.routeonomy.app.pleos.WindowAndFocusProbe;
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
    private WindowAndFocusProbe windowFocusProbe; // AVD experiment: adjacent navi window, audio focus
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
    // Real position (Pleos FusedLocation) and region/arrival fences; the simulation buttons stay as a backup.
    private FusedLocationSource locationSource;
    private GeofenceGate geofenceGate;
    private String locationSourceStatus = "위치: 시뮬레이션 입력";
    private GeoPoint lastRealFix;
    private String lastAlert;
    private TextView locationStatus;
    private List<GeoPoint> routePoints;
    private RouteMapView routeMap;
    private LinearLayout screen;
    private FrameLayout appArea;
    private int dashboardWidthDp = -1;
    private RegionPrediction prediction;
    private String regionSectionId;
    private String pendingAnnouncement;
    private boolean waitingForRegion;
    private boolean simulationOpen;
    private String preferredFirstId;
    private LinearLayout demoMovePanel;
    private Button enterButton;
    // Arrival quiz: null until started; dropped whenever P+ARRIVED no longer holds.
    private List<QuizQuestion> quizQuestions;
    private QuizSession quiz;
    private boolean quizEmptyNotice;
    private String renderedQuizKey;
    private LinearLayout quizPanel, quizChoices;
    private TextView quizQuestionText, quizFeedback;
    private Button quizNextButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        fake = new FakeVehicleInput(session);
        segments = new AssetSegmentRepository(this).load();
        quizQuestions = new AssetQuizRepository(this).load();
        for (BriefingSegment s : segments) if (s.getRegionId() != null) regionsWithContent.add(s.getRegionId());
        AssetRouteRepository routes = new AssetRouteRepository(this);
        regions = routes.regions();
        trip = routes.demoTrip();
        fakeRoute = new FakeRouteInput(trip, routes.simulationStops());
        routePoints = routes.routePoints();
        currentLocation = trip.getCurrent().getLocation();
        List<Geofence> fences = new ArrayList<>();
        for (Region r : regions) fences.add(Geofence.of(r));
        fences.add(Geofence.arrival(trip.getDestination()));
        geofenceGate = new GeofenceGate(fences);
        geofenceGate.onLocation(currentLocation); // simulated start: fences it is outside of can be entered
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
        locationSource = new FusedLocationSource(this, fences, new FusedLocationSource.Listener() {
            @Override public void onLocation(GeoPoint point) { runOnUiThread(() -> onRealLocation(point)); }
            @Override public void onFenceTransition(String fenceId, boolean entered) { runOnUiThread(() -> onProximityAlert(fenceId, entered)); }
            @Override public void onStatus(String message) { runOnUiThread(() -> { locationSourceStatus = message; render(); }); }
        });
        startLocation();
        windowFocusProbe = new WindowAndFocusProbe(this, message -> { naviProbe.note(message); return kotlin.Unit.INSTANCE; });
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
        locationStatus=label(13,ink); root.addView(locationStatus);
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
        enterButton=button("지역 진입"); enterButton.setOnClickListener(v->{
            Region target=entryTarget(); if(target==null) return;
            geofenceGate.onExit(target.getId()); // the jump starts outside the region, even if real tracking reset the gate
            fakeRoute.enterRegion(target); applyLocation(fakeRoute.getLocation());
        }); demoMovePanel.addView(enterButton,full());
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
        LinearLayout probeRow2=new LinearLayout(this);
        Button probeAdjacent=button("[실험] 내비 옆 창으로 열기"); probeAdjacent.setOnClickListener(v->windowFocusProbe.openNaviAdjacent()); probeRow2.addView(probeAdjacent,weight());
        Button probeFocus=button("[실험] 오디오 포커스 요청/반납"); probeFocus.setOnClickListener(v->windowFocusProbe.toggleFocus()); probeRow2.addView(probeFocus,weight());
        simulationPanel.addView(probeRow2);
        naviProbeStatus=label(13,ink); naviProbeStatus.setText("내비 실험 대기"); simulationPanel.addView(naviProbeStatus);
        simulationPanel.setVisibility(View.GONE); root.addView(simulationPanel);
        demoMovePanel.setVisibility(View.GONE); root.addView(demoMovePanel);

        quizStatus=label(18,ink); root.addView(quizStatus);
        quizButton=button("도착 후 퀴즈 시작"); quizButton.setOnClickListener(v->startQuiz()); root.addView(quizButton,full());
        quizPanel=new LinearLayout(this); quizPanel.setOrientation(LinearLayout.VERTICAL);
        quizQuestionText=label(22,ink); quizQuestionText.setTypeface(Typeface.DEFAULT,Typeface.BOLD); quizPanel.addView(quizQuestionText);
        quizChoices=new LinearLayout(this); quizChoices.setOrientation(LinearLayout.VERTICAL); quizPanel.addView(quizChoices);
        quizFeedback=label(18,ink); quizPanel.addView(quizFeedback);
        quizNextButton=button("다음 문제"); quizNextButton.setOnClickListener(v->onQuizNext()); quizPanel.addView(quizNextButton,full());
        quizPanel.setVisibility(View.GONE); root.addView(quizPanel);
        dashboardScroll = new ScrollView(this);
        dashboardScroll.setFillViewport(true);

        appArea = new FrameLayout(this);
        appArea.addView(dashboardScroll, new FrameLayout.LayoutParams(-1, -1));
        appArea.addView(briefingScroll, new FrameLayout.LayoutParams(-1, -1));
        routeMap = new RouteMapView(this);
        routeMap.setRoute(routePoints, trip.getCurrent(), trip.getDestination(), regions);
        screen = new LinearLayout(this);
        applyLayout();
        setContentView(screen);
    }

    private static final int WIDE_WINDOW_DP = 1200;
    private static final float MAP_SHARE = 0.45f;
    /** Full window: route panel left, app right. Narrower (e.g. half split): route panel on top. */
    private void applyLayout() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        boolean wide = widthDp >= WIDE_WINDOW_DP;
        screen.removeAllViews();
        screen.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        if (wide) {
            screen.addView(routeMap, new LinearLayout.LayoutParams(0, -1, MAP_SHARE));
            screen.addView(appArea, new LinearLayout.LayoutParams(0, -1, 1 - MAP_SHARE));
        } else {
            int mapHeight = Math.round(400 * getResources().getDisplayMetrics().density);
            screen.addView(routeMap, new LinearLayout.LayoutParams(-1, mapHeight));
            screen.addView(appArea, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        int appWidthDp = wide ? Math.round(widthDp * (1 - MAP_SHARE)) : widthDp;
        if (appWidthDp == dashboardWidthDp) return;
        // The dashboard sizes itself once at construction; rebuild it for the new column width.
        dashboardWidthDp = appWidthDp;
        dashboardScroll.removeAllViews();
        dashboard = new PreDriveDashboard(this, appWidthDp, this::startDashboardBriefing,
                this::changeDashboardTopic, () -> openTools(true), () -> openTools(false));
        dashboardScroll.addView(dashboard);
        CharSequence status = playbackStatus.getText();
        if (!ttsReady && status.toString().startsWith("Pleos")) dashboard.showStatus(status); // keep a TTS failure notice
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

    private void addMoveButton(Place stop){Button b=button(stop.getName()+"(으)로 이동");b.setOnClickListener(v->{fakeRoute.moveTo(stop);applyLocation(fakeRoute.getLocation());});demoMovePanel.addView(b,full());}
    /** The demo can only enter the region the trip is predicted to pass; nothing once a region was entered. */
    private Region entryTarget(){
        if (regionTransition.enteredRegionId() != null || prediction == null) return null;
        return prediction.getRegion();
    }
    private String regionName(String id){for(Region r:regions) if(r.getId().equals(id)) return r.getName(); return id;}

    private void onRealLocation(GeoPoint point) {
        lastRealFix = point;
        applyLocation(point);
    }
    /** Pleos proximity alert; the gate drops registration-time and duplicate events. */
    private void onProximityAlert(String fenceId, boolean entered) {
        lastAlert = fenceName(fenceId) + (entered ? " 진입" : " 이탈");
        boolean accepted = entered ? geofenceGate.onEnter(fenceId) : geofenceGate.onExit(fenceId);
        if (accepted && handleFence(fenceId, entered)) render();
        else refreshRoute();
    }
    /** Position update from Pleos FusedLocation or the simulation buttons: same path for both. */
    private void applyLocation(GeoPoint point) {
        currentLocation = point;
        fake.setEta(RemainingTimeEstimator.estimate(trip, point));
        boolean handled = false;
        for (GeofenceGate.Transition t : geofenceGate.onLocation(point)) handled |= handleFence(t.getId(), t.getEntered());
        if (handled) render();
        else refreshRoute();
    }
    /** Applies one accepted entry/exit. Returns true when it already replanned (region switch started now). */
    private boolean handleFence(String fenceId, boolean entered) {
        if (Geofence.DESTINATION_ID.equals(fenceId)) {
            fake.markArrived(entered); // leaving the destination again clears arrival
            return false;
        }
        if (!entered || !regionTransition.onRegionEntered(fenceId)) return false;
        if (playbackActive || playbackPaused) return false; // pending: switches after the current segment
        // Nothing is playing (not started, waiting for the region, or finished): switch now.
        regionTransition.consumePendingSwitch();
        startRegionSection(fenceId);
        replan();
        render();
        if (briefingStarted && ttsReady && !plan.getSegments().isEmpty()) speakCurrent();
        return true;
    }
    private String fenceName(String fenceId) {
        return Geofence.DESTINATION_ID.equals(fenceId) ? trip.getDestination().getName() : regionName(fenceId);
    }
    private String locationStatusText() {
        StringBuilder text = new StringBuilder(locationSourceStatus);
        if (lastRealFix != null) text.append(String.format(Locale.US, " · %.5f, %.5f", lastRealFix.getLatitude(), lastRealFix.getLongitude()));
        if (lastAlert != null) text.append("\n최근 Pleos 근접 알림: ").append(lastAlert);
        return text.toString();
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
        String route = routeStatusText();
        routeStatus.setText(route);
        String mapRegion = regionTransition.enteredRegionId() != null ? regionTransition.enteredRegionId()
                : prediction != null ? prediction.getRegion().getId() : null;
        routeMap.setState(currentLocation, mapRegion, regionTransition.enteredRegionId() != null,
                route.substring(route.indexOf('\n') + 1),
                locationSourceStatus + " · " + trip.getDestination().getName() + "까지 " + session.etaMinutes() + "분");
        locationStatus.setText(locationStatusText());
        locationStatus.setVisibility(driving?View.GONE:View.VISIBLE);
        topic.setText(current == null ? "브리핑 없음" : current.getTopic());
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
        boolean quizAllowed = session.canStartQuiz();
        if (!quizAllowed) { quiz = null; quizEmptyNotice = false; } // left P or arrival was cleared: stop the quiz
        quizButton.setVisibility(driving || quiz != null ? View.GONE : View.VISIBLE);
        quizStatus.setVisibility(driving?View.GONE:View.VISIBLE);
        quizButton.setEnabled(quizAllowed);
        if (quiz == null) quizStatus.setText(!quizAllowed ? "P단이어도 목적지 도착 전에는 퀴즈를 시작할 수 없습니다."
                : quizEmptyNotice ? "아직 끝까지 들은 브리핑이 없어 풀 수 있는 퀴즈가 없습니다." : "목적지 도착 확인 · 정차 중 퀴즈 가능");
        renderQuiz();
        if (!playbackPaused && !playbackActive && !waitingForRegion && playbackStatus != null && !playbackStatus.getText().toString().startsWith("재생 완료") && !playbackStatus.getText().toString().startsWith("Pleos TTS 오류") && !playbackStatus.getText().toString().startsWith("Pleos TTS 초기화 실패")) playbackStatus.setText(ttsReady?"브리핑 재생 준비":"TTS 초기화 중");
        if(pauseButton!=null) pauseButton.setText(playbackPaused?"재개":"재생/일시정지");
    }
    private void startQuiz(){
        if (!session.canStartQuiz()) return;
        QuizSession next = QuizSession.fromCompleted(quizQuestions, completedSegmentIds);
        if (next.isEmpty()) {
            quiz = null;
            quizEmptyNotice = true;
            render();
            return;
        }
        quiz = next;
        quizEmptyNotice = false;
        quizStatus.setText("오늘 들은 브리핑 퀴즈 · " + quiz.total() + "문제");
        render();
        // Scroll once the panel has been laid out, so the target offset is current.
        briefingScroll.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                briefingScroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                briefingScroll.smoothScrollTo(0, quizStatus.getTop());
            }
        });
    }
    private void onQuizAnswer(int choice){
        if (quiz == null || quiz.isFinished() || quiz.isAnswered()) return;
        quiz.answer(choice);
        renderQuiz();
    }
    private void onQuizNext(){
        if (quiz == null) return;
        if (quiz.isFinished()) quiz.restart();
        else if (quiz.isAnswered()) quiz.next();
        renderQuiz();
    }
    private void renderQuiz(){
        quizPanel.setVisibility(quiz == null ? View.GONE : View.VISIBLE);
        // render() runs on many unrelated events; rebuild the choices only when the quiz state changed.
        String key = quiz == null ? null : System.identityHashCode(quiz) + ":" + quiz.position() + ":" + quiz.selectedChoice();
        if (java.util.Objects.equals(key, renderedQuizKey)) return;
        renderedQuizKey = key;
        quizChoices.removeAllViews();
        if (quiz == null) return;
        if (quiz.isFinished()) {
            quizQuestionText.setText("퀴즈 결과 · " + quiz.total() + "문제 중 " + quiz.score() + "개 정답");
            quizFeedback.setText(quiz.score() == quiz.total() ? "모두 맞혔어요! 오늘 브리핑을 잘 기억하고 계시네요." : "틀린 문제는 오늘의 발견에서 다시 확인해 보세요.");
            quizNextButton.setText("다시 풀기");
            quizNextButton.setVisibility(View.VISIBLE);
            return;
        }
        QuizQuestion question = quiz.current();
        quizQuestionText.setText("문제 " + (quiz.position() + 1) + "/" + quiz.total() + "\n" + question.getQuestion());
        Integer selected = quiz.selectedChoice();
        List<String> choices = question.getChoices();
        for (int i = 0; i < choices.size(); i++) {
            final int choice = i;
            String mark = "";
            if (selected != null && i == question.getAnswerIndex()) mark = "✓ ";
            else if (selected != null && i == selected) mark = "✗ ";
            Button b = button(mark + (i + 1) + ". " + choices.get(i));
            b.setAllCaps(false);
            b.setEnabled(selected == null);
            b.setOnClickListener(v -> onQuizAnswer(choice));
            quizChoices.addView(b, full());
        }
        if (selected == null) {
            quizFeedback.setText("");
            quizNextButton.setVisibility(View.GONE);
        } else {
            boolean correct = selected == question.getAnswerIndex();
            quizFeedback.setText((correct ? "정답입니다!" : "아쉬워요. 정답은 " + (question.getAnswerIndex() + 1) + "번입니다.") + "\n" + question.getExplanation());
            quizNextButton.setText(quiz.position() + 1 < quiz.total() ? "다음 문제" : "결과 보기");
            quizNextButton.setVisibility(View.VISIBLE);
        }
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
        if (completed) { completedSegmentIds.add(previous.getId()); quizEmptyNotice = false; }
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
    private static final int FUSED_PERMISSION_REQUEST = 41;
    private void startLocation(){
        if (checkSelfPermission(FusedLocationSource.PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationSourceStatus = "위치: Pleos 위치 권한 요청 중";
            requestPermissions(new String[]{FusedLocationSource.PERMISSION}, FUSED_PERMISSION_REQUEST);
            return;
        }
        // Real tracking replaces the simulated start: entries count only after a fix outside the fence.
        geofenceGate.reset();
        locationSource.start();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != FUSED_PERMISSION_REQUEST) return;
        boolean granted = results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (granted) startLocation();
        else locationSourceStatus = "위치: 권한 거부 · 시뮬레이션 입력으로 진행";
        render();
    }
    /** Window resized/moved (Pleos window bar): the activity is kept; log the new window for the experiment. */
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        applyLayout();
        render();
        if (naviProbe != null) naviProbe.note("창 변경: " + config.screenWidthDp + "x" + config.screenHeightDp + "dp, 멀티윈도우=" + isInMultiWindowMode());
    }
    @Override public void onMultiWindowModeChanged(boolean inMultiWindow, android.content.res.Configuration config) {
        super.onMultiWindowModeChanged(inMultiWindow, config);
        if (naviProbe != null) naviProbe.note("멀티윈도우 모드 변경: " + inMultiWindow);
    }
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.release();}if(gearMonitor!=null)gearMonitor.close();if(naviEtaClient!=null)naviEtaClient.close();if(locationSource!=null)locationSource.stop();if(windowFocusProbe!=null)windowFocusProbe.close();if(naviVoiceMonitor!=null)naviVoiceMonitor.close();if(naviProbe!=null)naviProbe.close();super.onDestroy();}
}
