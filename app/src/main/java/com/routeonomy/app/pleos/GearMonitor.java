package com.routeonomy.app.pleos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.Gear;
import ai.pleos.playground.vehicle.constant.control.VehicleGear;
import ai.pleos.playground.vehicle.listener.GearSelectionListener;
import com.routeonomy.app.domain.DriveSession;
import kotlin.Unit;
import java.util.function.Consumer;

/** Reads Pleos Vehicle SDK gear state and subscription events. */
public final class GearMonitor {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Vehicle vehicle;
    private Gear gearApi;
    private final Consumer<DriveSession.Gear> onGear;
    private final Consumer<String> onError;
    private final GearSelectionListener listener = new GearSelectionListener() {
        @Override public void onSelected(VehicleGear value) { deliver(value); }
        @Override public void onFailed(Exception error) { report("Pleos 기어 구독 실패: " + error.getMessage()); }
    };

    public GearMonitor(Context context, Consumer<DriveSession.Gear> onGear, Consumer<String> onError) {
        this.vehicle = new Vehicle(context.getApplicationContext());
        this.onGear = onGear;
        this.onError = onError;
        vehicle.initialize();
        try {
            gearApi = vehicle.getGear();
            gearApi.registerGearSelection(listener);
            gearApi.getCurrentGearSelectionState(value -> { deliver(value); return Unit.INSTANCE; }, error -> { report("Pleos 기어 조회 실패: " + error.getMessage()); return Unit.INSTANCE; });
        } catch (Exception error) {
            report("Pleos 기어 초기화 실패: " + error.getMessage());
        }
    }

    public void close() {
        if (gearApi != null) {
            try { gearApi.unregisterGearSelection(listener); }
            catch (Exception error) { report("기어 리스너 해제 실패: " + error.getMessage()); }
        }
        vehicle.release();
    }

    private void deliver(VehicleGear value) {
        if (value == null) return;
        DriveSession.Gear mapped;
        switch (value) {
            case GEAR_PARK: mapped = DriveSession.Gear.PARK; break;
            case GEAR_DRIVE: mapped = DriveSession.Gear.DRIVE; break;
            case GEAR_REVERSE: mapped = DriveSession.Gear.REVERSE; break;
            case GEAR_NEUTRAL: mapped = DriveSession.Gear.NEUTRAL; break;
            default: return;
        }
        main.post(() -> onGear.accept(mapped));
    }

    private void report(String message) { main.post(() -> onError.accept(message)); }
}
