package com.routeonomy.app.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import com.routeonomy.app.data.AssetQuizRepository;
import com.routeonomy.app.data.AssetRouteRepository;
import com.routeonomy.app.data.AssetSegmentRepository;
import com.routeonomy.app.data.PrefsProgressStore;
import com.routeonomy.app.domain.BriefingSegment;
import com.routeonomy.app.domain.BriefingSession;
import com.routeonomy.app.domain.DriveSession;
import com.routeonomy.app.domain.GeoPoint;
import com.routeonomy.app.domain.Geofence;
import com.routeonomy.app.domain.GeofenceGate;
import com.routeonomy.app.domain.Place;
import com.routeonomy.app.domain.PlayerProgress;
import com.routeonomy.app.domain.QuizQuestion;
import com.routeonomy.app.domain.QuizSession;
import com.routeonomy.app.domain.Region;
import com.routeonomy.app.domain.RegionPrediction;
import com.routeonomy.app.domain.RegionPredictor;
import com.routeonomy.app.domain.RemainingTimeEstimator;
import com.routeonomy.app.domain.TripContext;
import com.routeonomy.app.domain.TodayDiscoveries;
import com.routeonomy.app.pleos.GearMonitor;
import com.routeonomy.app.pleos.NaviEtaClient;
import com.routeonomy.app.pleos.FusedLocationSource;
import com.routeonomy.app.pleos.PleosTtsSpeaker;
import com.routeonomy.app.simulation.FakeRouteInput;
import com.routeonomy.app.simulation.FakeVehicleInput;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private final DriveSession session = new DriveSession();
    private List<BriefingSegment> segments;
    // Queue, current sentence, finished segments and the A→B switch (F3/F4/F5/F7).
    private BriefingSession briefing;
    // Points and growth stage, kept across drives.
    private PlayerProgress progress;
    private PleosTtsSpeaker speaker;
    private FakeVehicleInput fake;
    private GearMonitor gearMonitor;
    private NaviEtaClient naviEtaClient;
    private TextView details, quizStatus, sdkStatus, playbackStatus, discoveries;
    private NowPlayingCard nowPlaying;
    private Button quizButton;
    private LinearLayout simulationPanel;
    private Button debugToggleButton;
    private PreDriveDashboard dashboard;
    private ScrollView dashboardScroll;
    private ScrollView briefingScroll;
    private boolean toolsOpen;
    private boolean detailsOpen; // P: script, discoveries, quiz entry and location under the Now Playing screen
    private boolean ttsReady;
    private String ttsProblem; // init failure / service not connected, shown until TTS is ready
    private String ttsError;   // last speech error, shown until playback restarts
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
    private boolean simulationOpen;
    private LinearLayout demoMovePanel;
    // Arrival quiz: null until started; dropped whenever P+ARRIVED no longer holds.
    private List<QuizQuestion> quizQuestions;
    private QuizSession quiz;
    private boolean quizHidden; // closed with X: kept so it can be resumed
    private boolean quizEmptyNotice;
    private String renderedQuizKey;
    private QuizScreen quizScreen;
    private ArrivalPopup arrivalPopup;
    private ConfirmPopup homeConfirm;
    // Home (pre-drive dashboard) opened from the briefing screen; audio is paused while it shows.
    private boolean homeShown;
    private boolean arrivalCelebrated; // the popup is shown once per app run
    private ScrollView quizScroll;
    // Points given for the last submitted answer, for the quiz feedback line.
    private QuizSession awardQuiz;
    private int awardPosition = -1;
    private int awardPoints;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        fake = new FakeVehicleInput(session);
        segments = new AssetSegmentRepository(this).load();
        quizQuestions = new AssetQuizRepository(this).load();
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
        speaker = new PleosTtsSpeaker(this, new PleosTtsSpeaker.Listener() {
            @Override public void onReady() {
                ttsReady = true;
                briefing.setSpeechReady(true);
                dashboard.showStatus("");
                render();
            }
            @Override public void onInitFailed(String message) {
                ttsProblem = "Pleos TTS 초기화 실패 · " + message;
                dashboard.showStatus(ttsProblem);
                render();
            }
            @Override public void onDone(int utteranceId) {
                int heard = briefing.completedSegmentIds().size();
                briefing.onUtteranceDone(utteranceId);
                if (briefing.completedSegmentIds().size() > heard) quizEmptyNotice = false;
                render();
            }
            @Override public void onError(int utteranceId, String message) {
                ttsError = "Pleos TTS 오류 · " + message;
                briefing.onUtteranceError(utteranceId);
                render();
            }
        });
        briefing = new BriefingSession(segments, regions, trip.getDestination().getLocation(), currentLocation,
                session.etaMinutes(), speaker, () -> android.os.SystemClock.elapsedRealtime());
        progress = new PlayerProgress(new PrefsProgressStore(this));
        briefing.setCompletionListener(segment -> {
            progress.rewardSegment(segment.getId(), segment.getType());
            return kotlin.Unit.INSTANCE;
        });
        buildUi();
        render();
        speaker.initialize();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!ttsReady && ttsProblem == null) {
                ttsProblem = "Pleos 차량 TTS 서비스에 연결되지 않았습니다.";
                dashboard.showStatus(ttsProblem);
                render();
            }
        }, 5000);
        gearMonitor = new GearMonitor(this, gear -> { session.setGear(gear); runOnUiThread(this::render); }, message -> runOnUiThread(() -> sdkStatus.setText(message)));
        naviEtaClient = new NaviEtaClient(this,
            (rawTime, meters) -> { runOnUiThread(() -> sdkStatus.setText("NaviHelper 응답 · remainTime(raw)=" + rawTime + " · remainDistance=" + meters + "m")); return kotlin.Unit.INSTANCE; },
            message -> { runOnUiThread(() -> sdkStatus.setText(message)); return kotlin.Unit.INSTANCE; });
        naviEtaClient.start();
        locationSource = new FusedLocationSource(this, fences, new FusedLocationSource.Listener() {
            @Override public void onLocation(GeoPoint point) { runOnUiThread(() -> onRealLocation(point)); }
            @Override public void onFenceTransition(String fenceId, boolean entered) { runOnUiThread(() -> onProximityAlert(fenceId, entered)); }
            @Override public void onStatus(String message) { runOnUiThread(() -> { locationSourceStatus = message; render(); }); }
        });
        startLocation();
    }

    private void buildUi() {
        int ink=UiStyle.INK;
        UiStyle style = new UiStyle(this, 1f);
        briefingScroll = new ScrollView(this);
        briefingScroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(style.size(28),style.size(24),style.size(28),style.size(24)); root.setBackgroundColor(UiStyle.BACKGROUND); briefingScroll.addView(root);
        nowPlaying = new NowPlayingCard(this, this::requestHome, this::togglePlayback,
                this::previousTopic, this::nextTopic, this::toggleSimulationFromCard, this::toggleDetails);
        root.addView(nowPlaying, full());
        playbackStatus = nowPlaying.playbackStatusView();
        // P only: script, discoveries, quiz entry and tools below the Now Playing card.
        details=infoCard(style); discoveries=infoCard(style);
        locationStatus=label(13,UiStyle.MUTED);

        debugToggleButton=button("시뮬레이션 입력 열기");
        root.addView(details,cardParams(style)); root.addView(discoveries,cardParams(style)); root.addView(locationStatus);
        simulationPanel=new LinearLayout(this); simulationPanel.setOrientation(LinearLayout.VERTICAL);
        debugToggleButton.setOnClickListener(v->{simulationOpen=!simulationOpen;debugToggleButton.setText(simulationOpen?"시뮬레이션 입력 닫기":"시뮬레이션 입력 열기");render();});
        root.addView(debugToggleButton,full());
        LinearLayout gears=new LinearLayout(this);
        addGearButton(gears,"P",DriveSession.Gear.PARK); addGearButton(gears,"R",DriveSession.Gear.REVERSE);
        addGearButton(gears,"N",DriveSession.Gear.NEUTRAL); addGearButton(gears,"D",DriveSession.Gear.DRIVE);
        simulationPanel.addView(gears);
        // Demo only (video): set the points before recording so a stage-up can be shown.
        LinearLayout pointControls=new LinearLayout(this);
        Button addPoints=button("포인트 +100"); addPoints.setOnClickListener(v->{progress.addDemoPoints(100);render();}); pointControls.addView(addPoints,weight());
        Button nearNext=button("다음 단계 직전"); nearNext.setOnClickListener(v->{progress.moveNearNextStage();render();}); pointControls.addView(nearNext,weight());
        Button resetPoints=button("포인트 초기화"); resetPoints.setOnClickListener(v->{progress.resetAll();render();}); pointControls.addView(resetPoints,weight());
        simulationPanel.addView(pointControls);
        // Demo-only movement row. It is the one simulation control kept on the driving screen.
        demoMovePanel=new LinearLayout(this); demoMovePanel.setOrientation(LinearLayout.VERTICAL);
        List<Place> stops = fakeRoute.stops();
        if (!stops.isEmpty()) addMoveButton(stops.get(0));
        if (stops.size() > 1) addMoveButton(stops.get(stops.size()-1));
        Button etaRefresh=button("NaviHelper ETA 조회"); etaRefresh.setOnClickListener(v->naviEtaClient.refresh()); simulationPanel.addView(etaRefresh,full());
        sdkStatus=label(13,ink); simulationPanel.addView(sdkStatus);
        simulationPanel.setVisibility(View.GONE); root.addView(simulationPanel);
        demoMovePanel.setVisibility(View.GONE); root.addView(demoMovePanel);

        quizStatus=label(18,ink); root.addView(quizStatus,1);
        quizButton=button("도착 후 퀴즈 시작"); quizButton.setOnClickListener(v->startQuiz()); root.addView(quizButton,2,full());
        quizScreen = new QuizScreen(this, this::requestHome, this::closeQuiz, this::openSimulationFromQuiz, this::onQuizAnswer, this::onQuizNext);
        quizScreen.setPadding(style.size(28), style.size(24), style.size(28), style.size(24));
        quizScroll = new ScrollView(this);
        quizScroll.setFillViewport(true);
        quizScroll.setBackgroundColor(UiStyle.BACKGROUND);
        quizScroll.addView(quizScreen);
        quizScroll.setVisibility(View.GONE);
        dashboardScroll = new ScrollView(this);
        dashboardScroll.setFillViewport(true);

        appArea = new FrameLayout(this);
        appArea.addView(dashboardScroll, new FrameLayout.LayoutParams(-1, -1));
        appArea.addView(briefingScroll, new FrameLayout.LayoutParams(-1, -1));
        appArea.addView(quizScroll, new FrameLayout.LayoutParams(-1, -1));
        arrivalPopup = new ArrivalPopup(this, () -> {
            arrivalPopup.hide();
            startQuiz();
        }, () -> { arrivalPopup.hide(); updateBackCallback(); });
        appArea.addView(arrivalPopup, new FrameLayout.LayoutParams(-1, -1));
        homeConfirm = new ConfirmPopup(this, "홈 화면으로 갈까요?",
                "홈 화면으로 가면 음성 재생이 멈춥니다.\n그래도 홈으로 가시겠습니까?", "뒤로가기");
        appArea.addView(homeConfirm, new FrameLayout.LayoutParams(-1, -1));
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
                () -> shiftDashboardTopic(1), () -> shiftDashboardTopic(-1), () -> shiftDashboardTopic(1),
                () -> openTools(true), () -> openTools(false));
        dashboardScroll.addView(dashboard);
        if (!ttsReady && ttsProblem != null) dashboard.showStatus(ttsProblem); // keep a TTS failure notice
    }

    private void toggleSimulationFromCard() {
        simulationOpen = !simulationOpen;
        debugToggleButton.setText(simulationOpen ? "시뮬레이션 입력 닫기" : "시뮬레이션 입력 열기");
        render();
        if (simulationOpen) briefingScroll.post(() -> briefingScroll.smoothScrollTo(0, debugToggleButton.getTop()));
    }

    private void toggleDetails() {
        detailsOpen = !detailsOpen;
        render();
        if (detailsOpen) briefingScroll.post(() -> briefingScroll.smoothScrollTo(0, details.getTop()));
    }

    private void togglePlayback() {
        briefing.togglePlayback();
        render();
    }

    private TextView infoCard(UiStyle style) {
        TextView card = style.text("", 17, UiStyle.INK, false);
        card.setLineSpacing(style.size(4), 1f);
        card.setPadding(style.size(20), style.size(16), style.size(20), style.size(16));
        style.card(card);
        return card;
    }

    private LinearLayout.LayoutParams cardParams(UiStyle style) {
        LinearLayout.LayoutParams params = full();
        params.topMargin = style.size(16);
        return params;
    }

    /** Home button / system back on the briefing screen: confirm first when audio is in progress. */
    private void requestHome() {
        if (briefing.isPlaying()) { homeConfirm.show(this::goHome); updateBackCallback(); }
        else goHome();
    }

    private void goHome() {
        briefing.pause(); // resumes from the current sentence via the dashboard start button
        setHomeShown(true);
        toolsOpen = false;
        quizHidden = quiz != null || quizHidden;
        render();
    }

    // System back (targetSdk 37: predictive back, onBackPressed is not called). Registered only while
    // back has an in-app meaning; on the home screen the system default (leave the app) applies.
    private Object backCallback;
    private boolean backRegistered;
    private void updateBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        boolean want = homeConfirm.isShowing() || arrivalPopup.isShowing() || dashboardScroll.getVisibility() != View.VISIBLE;
        if (want == backRegistered) return;
        if (backCallback == null) backCallback = (android.window.OnBackInvokedCallback) this::onSystemBack;
        android.window.OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
        if (want) dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                (android.window.OnBackInvokedCallback) backCallback);
        else dispatcher.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
        backRegistered = want;
    }
    private void onSystemBack() {
        if (homeConfirm.isShowing()) homeConfirm.hide();
        else if (arrivalPopup.isShowing()) arrivalPopup.hide();
        else if (dashboardScroll.getVisibility() == View.VISIBLE) moveTaskToBack(true); // already home
        else if (quiz != null && !quizHidden) closeQuiz();
        else requestHome();
        updateBackCallback();
    }

    /** Home shown over the briefing: audio is paused, and route events must not start it by themselves. */
    private void setHomeShown(boolean shown) {
        homeShown = shown;
        briefing.setAutoplayHeld(shown);
    }

    private void openTools(boolean showSimulation) {
        setHomeShown(false);
        toolsOpen = true;
        detailsOpen = true;
        simulationOpen = showSimulation;
        debugToggleButton.setText(showSimulation ? "시뮬레이션 입력 닫기" : "시뮬레이션 입력 열기");
        render();
    }

    private void startDashboardBriefing() {
        if (!ttsReady) {
            dashboard.showStatus(playbackText());
            return;
        }
        setHomeShown(false);
        briefing.startFromDashboard(); // resumes after home, else starts with the banner topic
        render();
    }

    /** Banner swipe / Change topic: picks the next (+1) or previous (-1) page; it plays first on start. */
    private void shiftDashboardTopic(int delta) {
        briefing.shiftDashboardTopic(delta);
        render();
    }

    private void addMoveButton(Place stop){Button b=button(stop.getName()+"(으)로 이동");b.setOnClickListener(v->{fakeRoute.moveTo(stop);applyLocation(fakeRoute.getLocation());});demoMovePanel.addView(b,full());}
    private String regionName(String id){for(Region r:regions) if(r.getId().equals(id)) return r.getName(); return id;}

    private void onRealLocation(GeoPoint point) {
        // The first real fix replaces the simulated start: fences count only after a real fix outside them.
        // Until then the simulation buttons keep working as a backup.
        if (lastRealFix == null) geofenceGate.reset();
        lastRealFix = point;
        applyLocation(point);
    }
    /** Pleos proximity alert; the gate drops registration-time and duplicate events. */
    private void onProximityAlert(String fenceId, boolean entered) {
        if (lastRealFix == null) return; // registration-time alert before the first real fix
        boolean accepted = entered ? geofenceGate.onEnter(fenceId) : geofenceGate.onExit(fenceId);
        if (!accepted) return;
        lastAlert = fenceName(fenceId) + (entered ? " 진입" : " 이탈");
        if (handleFence(fenceId, entered)) render();
        else refreshRoute();
    }
    /** Position update from Pleos FusedLocation or the simulation buttons: same path for both. */
    private void applyLocation(GeoPoint point) {
        currentLocation = point;
        fake.setEta(RemainingTimeEstimator.estimate(trip, point));
        briefing.updatePosition(point, session.etaMinutes());
        boolean handled = false;
        for (GeofenceGate.Transition t : geofenceGate.onLocation(point)) handled |= handleFence(t.getId(), t.getEntered());
        if (handled) render();
        else refreshRoute();
    }
    /** Applies one accepted entry/exit. Returns true when it already replanned (region switch started now). */
    private boolean handleFence(String fenceId, boolean entered) {
        if (Geofence.DESTINATION_ID.equals(fenceId)) {
            if (entered) fake.markArrived(true); // sticky: GPS jitter at the edge must not drop a quiz in progress
            return false;
        }
        // Switches now when nothing is playing; otherwise after the current segment.
        return entered && briefing.onRegionEntered(fenceId);
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
        briefing.updatePosition(currentLocation, session.etaMinutes());
        briefing.refreshRoute();
        render();
    }
    private String routeStatusText() {
        String dest = "→ " + trip.getDestination().getName();
        String regionSectionId = briefing.regionSectionId();
        RegionPrediction prediction = briefing.prediction();
        if (briefing.hasPendingRegionSwitch()) return dest + "\n" + regionName(briefing.enteredRegionId()) + " 진입 · 지금 이야기가 끝나면 지역 브리핑으로 넘어가요";
        if (regionSectionId != null) {
            Region here = RegionPredictor.regionAt(currentLocation, regions);
            boolean inside = here != null && here.getId().equals(regionSectionId);
            return dest + "\n" + regionName(regionSectionId) + (inside ? " 구간 · 지역 브리핑" : " 구간 통과");
        }
        if (prediction != null) {
            String name = prediction.getRegion().getName();
            if (briefing.isWaitingForRegion()) return dest + "\n" + name + " 진입 대기 · 들어서면 지역 이야기를 이어서 들려드려요";
            return dest + "\n" + name + " 경유 예상 · 약 " + prediction.getEstimatedMinutesToRegion() + "분 뒤 (직선 거리 기준 추정)";
        }
        return dest + "\n경유 예상 지역 없음";
    }
    private void addGearButton(LinearLayout row,String title,DriveSession.Gear gear){Button b=button(title);b.setOnClickListener(v->{fake.setGear(gear);render();});row.addView(b,weight());}
    private TextView label(int size,int color){TextView v=new TextView(this);v.setTextSize(size);v.setTextColor(color);int pad=Math.round(8*getResources().getDisplayMetrics().density);v.setPadding(0,pad,0,pad);return v;}
    private Button button(String title){Button b=new Button(this);b.setText(title);return b;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private void render(){
        boolean driving = session.isDriving();
        boolean preDrive = !driving && !briefing.hasStarted() && !session.canStartQuiz();
        boolean showDashboard = (preDrive && !toolsOpen) || homeShown;
        dashboardScroll.setVisibility(showDashboard ? View.VISIBLE : View.GONE);
        // Quiz state is settled further down (it may be dropped); the screens are switched in renderQuiz().
        briefingScroll.setVisibility(showDashboard ? View.GONE : View.VISIBLE);
        nowPlaying.setDriving(driving);
        BriefingSegment current = briefing.currentSegment();
        BriefingSegment shownTopic = briefing.dashboardTopic();
        dashboard.bindTopic(shownTopic, briefing.canChangeDashboardTopic());
        String route = routeStatusText();
        boolean arrived = session.arrival() == DriveSession.Arrival.ARRIVED;
        int driveLeft = arrived ? 0 : session.etaMinutes(); // the estimator never goes below 1 minute
        float tripProgress = 1f - driveLeft / (float) Math.max(1, trip.getRemainingMinutes());
        String hook = TopicVisuals.of(current).question;
        nowPlaying.bind(current, hook != null ? hook : firstSentence(current), (int) (briefing.studyMillis() / 60_000), driveLeft, tripProgress);
        Integer toNext = progress.pointsToNextStage();
        nowPlaying.setLevel(stageText(), (toNext == null ? "마지막 단계" : "다음 단계까지 " + toNext + "P")
                + " · 보유 " + progress.balance() + "P");
        dashboard.setStageName(progress.stage().getLabel());
        String entered = briefing.enteredRegionId();
        RegionPrediction prediction = briefing.prediction();
        String mapRegion = entered != null ? entered : prediction != null ? prediction.getRegion().getId() : null;
        routeMap.setState(currentLocation, mapRegion, entered != null,
                route.substring(route.indexOf('\n') + 1),
                locationSourceStatus + " · " + trip.getDestination().getName() + "까지 " + session.etaMinutes() + "분");
        locationStatus.setText(locationStatusText());
        boolean showDetails = !driving && (detailsOpen || simulationOpen);
        locationStatus.setVisibility(showDetails?View.VISIBLE:View.GONE);
        details.setText(current == null ? "브리핑 원고 없음" : "원고\n" + current.getScript() + "\n\n새 용어 · " + current.getTerm().getName() + "\n" + current.getTerm().getExplanation());
        details.setVisibility(showDetails?View.VISIBLE:View.GONE);
        discoveries.setText(discoveryText());
        discoveries.setVisibility(showDetails?View.VISIBLE:View.GONE);
        debugToggleButton.setVisibility(showDetails?View.VISIBLE:View.GONE);
        simulationPanel.setVisibility(simulationOpen && !driving ? View.VISIBLE : View.GONE);
        // Demo-only: the movement row stays available in D so the region switch can be shown while driving.
        demoMovePanel.setVisibility(simulationOpen ? View.VISIBLE : View.GONE);
        boolean quizAllowed = session.canStartQuiz();
        if (!quizAllowed) {
            quiz = null; quizEmptyNotice = false; arrivalPopup.hide(); // left P or arrival was cleared: stop the quiz
            // A cleared (simulated) arrival must not use up the popup of the real arrival.
            if (session.arrival() != DriveSession.Arrival.ARRIVED) arrivalCelebrated = false;
        }
        else if (!arrivalCelebrated) {
            // First time arrival and P hold together: praise, then offer the quiz.
            arrivalCelebrated = true;
            com.routeonomy.app.domain.GrowthStage grown = progress.stageReachedThisRun();
            arrivalPopup.show(briefing.completedSegmentIds().size(),
                    !QuizSession.fromCompleted(quizQuestions, briefing.completedSegmentIds()).isEmpty(),
                    "이번 주행 +" + progress.earnedThisRun() + " 포인트 · 지금 " + stageText()
                            + (grown == null ? "" : "\n" + grown.getEmoji() + " " + grown.getLabel() + "(으)로 자랐어요!"));
        }
        quizButton.setVisibility(!showDetails || (quiz != null && !quizHidden) ? View.GONE : View.VISIBLE);
        quizButton.setText(quiz != null && quizHidden && !quiz.isFinished() ? "퀴즈 이어 풀기" : "도착 후 퀴즈 시작");
        quizStatus.setVisibility(showDetails?View.VISIBLE:View.GONE);
        quizButton.setEnabled(quizAllowed);
        if (quiz == null || quizHidden) quizStatus.setText(!quizAllowed ? "P단이어도 목적지 도착 전에는 퀴즈를 시작할 수 없습니다."
                : quizEmptyNotice ? "아직 끝까지 들은 브리핑이 없어 풀 수 있는 퀴즈가 없습니다." : "목적지 도착 확인 · 정차 중 퀴즈 가능");
        renderQuiz();
        playbackStatus.setText(playbackText());
        nowPlaying.setPlaying(briefing.isPlaying());
        updateBackCallback();
    }
    private void startQuiz(){
        if (!session.canStartQuiz()) return;
        briefing.pause(); // same for the arrival popup and the quiz button
        setHomeShown(false); // e.g. from the arrival popup shown over the home screen
        if (quiz != null && quizHidden && !quiz.isFinished()) { quizHidden = false; render(); return; } // resume
        QuizSession next = QuizSession.fromCompleted(quizQuestions, briefing.completedSegmentIds());
        if (next.isEmpty()) {
            quiz = null;
            quizEmptyNotice = true;
            render();
            return;
        }
        quiz = next;
        quizHidden = false;
        quizEmptyNotice = false;
        render();
        quizScroll.scrollTo(0, 0);
    }
    private void openSimulationFromQuiz(){
        quizHidden = true;
        if (!simulationOpen) toggleSimulationFromCard(); else render();
    }
    private void closeQuiz(){
        quizHidden = true;
        render();
    }
    private void onQuizAnswer(int choice){
        if (quiz == null || quiz.isFinished() || quiz.isAnswered()) return;
        String segmentId = quiz.current().getSegmentId();
        boolean correct = quiz.answer(choice);
        awardQuiz = quiz;
        awardPosition = quiz.position();
        awardPoints = progress.rewardQuizAnswer(segmentId, correct); // first answer per question per drive
        renderQuiz();
    }
    private void onQuizNext(){
        if (quiz == null) return;
        if (quiz.isFinished()) { quiz.restart(); awardQuiz = null; }
        else if (quiz.isAnswered()) quiz.next();
        renderQuiz();
    }
    private void renderQuiz(){
        boolean showQuiz = quiz != null && !quizHidden && !homeShown;
        quizScroll.setVisibility(showQuiz ? View.VISIBLE : View.GONE);
        if (showQuiz) briefingScroll.setVisibility(View.GONE);
        // render() runs on many unrelated events; rebind only when the quiz state changed.
        // Points are in the key too: demo point buttons change whether the hint can be shown.
        String key = quiz == null ? null : System.identityHashCode(quiz) + ":" + quiz.position() + ":" + quiz.selectedChoice()
                + ":" + progress.balance() + ":" + quizRewards.isHintPaid();
        if (java.util.Objects.equals(key, renderedQuizKey)) return;
        renderedQuizKey = key;
        if (quiz != null) quizScreen.bind(quiz, quizHint(), quizRewards);
    }
    private final QuizScreen.Rewards quizRewards = new QuizScreen.Rewards() {
        private String segmentId() {
            QuizQuestion question = quiz == null ? null : quiz.current();
            return question == null ? null : question.getSegmentId();
        }
        @Override public boolean payHint() { String id = segmentId(); return id != null && progress.payHint(id); }
        @Override public boolean canShowHint() { String id = segmentId(); return id != null && progress.canShowHint(id); }
        @Override public boolean isHintPaid() { String id = segmentId(); return id != null && progress.isHintPaid(id); }
        @Override public int answerPoints() {
            return quiz == awardQuiz && quiz != null && quiz.position() == awardPosition ? awardPoints : 0;
        }
        @Override public String resultLine() {
            return "이번 퀴즈 +" + progress.quizPointsThisRun() + " 포인트 · 지금 " + stageText();
        }
    };
    /** e.g. "🌱 새싹 Lv.2". */
    private String stageText() {
        com.routeonomy.app.domain.GrowthStage stage = progress.stage();
        return stage.getEmoji() + " " + stage.getLabel() + " Lv." + stage.getLevel();
    }
    /** Hint for the current question: the new term of the briefing it came from. */
    private String quizHint(){
        QuizQuestion question = quiz.current();
        if (question == null) return null;
        for (BriefingSegment s : segments) if (s.getId().equals(question.getSegmentId()))
            return s.getTerm().getName() + " — " + s.getTerm().getExplanation();
        return null;
    }
    private String discoveryText(){
        List<BriefingSegment> heard = TodayDiscoveries.from(segments, briefing.completedSegmentIds());
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
    /** "다음 주제": skips the topic being played; before anything started it does nothing (no silent skip). */
    private void nextTopic(){
        briefing.nextTopic();
        render();
    }
    /** "이전 주제": replays the earlier topic from its start, or restarts the only one. */
    private void previousTopic(){
        briefing.previousTopic();
        render();
    }
    private String playbackText() {
        if (!ttsReady) return ttsProblem != null ? ttsProblem : "TTS 초기화 중";
        switch (briefing.state()) {
            case PLAYING: return "Pleos TTS 재생 중";
            case PAUSED: return "일시정지됨 · 현재 문장 처음부터 재개";
            case WAITING_FOR_REGION: {
                RegionPrediction prediction = briefing.prediction();
                return (prediction == null ? "지역" : prediction.getRegion().getName()) + " 진입 대기";
            }
            case FINISHED: return "재생 완료 · " + briefing.completedSegmentIds().size() + "개 항목 청취 기록";
            case NOTHING_LEFT: return "남은 브리핑이 없습니다";
            case ERROR: return ttsError != null ? ttsError : "Pleos TTS 오류";
            default: return "브리핑 재생 준비";
        }
    }
    private String firstSentenceId, firstSentenceText;
    /** Hook line under the Now Playing title; cached because render() runs on every location fix. */
    private String firstSentence(BriefingSegment segment) {
        if (segment == null) return null;
        if (!segment.getId().equals(firstSentenceId)) {
            List<String> sentences = BriefingSession.splitSentences(segment.getScript());
            firstSentenceId = segment.getId();
            firstSentenceText = sentences.isEmpty() ? "" : sentences.get(0);
        }
        return firstSentenceText;
    }
    private static final int FUSED_PERMISSION_REQUEST = 41;
    private void startLocation(){
        if (checkSelfPermission(FusedLocationSource.PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationSourceStatus = "위치: Pleos 위치 권한 요청 중";
            requestPermissions(new String[]{FusedLocationSource.PERMISSION}, FUSED_PERMISSION_REQUEST);
            return;
        }
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
    /** Window resized/moved (Pleos window bar): the activity is kept (configChanges); re-lay out the panels. */
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        applyLayout();
        render();
    }
    @Override protected void onDestroy(){if(speaker!=null)speaker.release();if(gearMonitor!=null)gearMonitor.close();if(naviEtaClient!=null)naviEtaClient.close();if(locationSource!=null)locationSource.stop();super.onDestroy();}
}
