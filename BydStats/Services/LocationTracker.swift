import Foundation
import CoreLocation

/// 주행 중 GPS 기반 거리 누적 추적기
final class LocationTracker: NSObject, CLLocationManagerDelegate, @unchecked Sendable {
    private let manager = CLLocationManager()
    private var lastLocation: CLLocation?
    private(set) var accumulatedDistanceKm: Double = 0

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        manager.distanceFilter = 10  // 10m 이상 이동 시에만 업데이트
    }

    func startTracking() {
        accumulatedDistanceKm = 0
        lastLocation = nil
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { return }
        manager.startUpdatingLocation()
    }

    /// 추적 중지 후 누적 거리(km) 반환
    func stopTracking() -> Double {
        manager.stopUpdatingLocation()
        let dist = accumulatedDistanceKm
        accumulatedDistanceKm = 0
        lastLocation = nil
        return dist
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let newLoc = locations.last,
              newLoc.horizontalAccuracy >= 0,
              newLoc.horizontalAccuracy < 50 else { return }

        if let last = lastLocation {
            let delta = newLoc.distance(from: last) / 1000.0
            // 1km 이상 점프는 GPS 노이즈로 간주
            if delta < 1.0 {
                accumulatedDistanceKm += delta
            }
        }
        lastLocation = newLoc
    }
}
