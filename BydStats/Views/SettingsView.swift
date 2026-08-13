import SwiftUI

struct SettingsView: View {
    @Environment(AppState.self) private var appState

    // 로그인 폼 입력
    @State private var username = ""
    @State private var password = ""
    @State private var region = "KR"

    // 기타 설정
    @AppStorage("vehicleModel")    private var vehicleModel    = VehicleModel.atto3.rawValue
    @AppStorage("electricityRate") private var electricityRate = 180.0
    @AppStorage("nightRate")       private var nightRate       = 120.0
    @AppStorage("pollingInterval") private var pollingInterval = 5
    @AppStorage("gpsEnabled")      private var gpsEnabled      = true

    var body: some View {
        NavigationStack {
            Form {
                accountSection
                if appState.isLoggedIn {
                    vehicleSection
                }
                vehicleModelSection
                electricitySection
                pollingSection
                locationSection
            }
            .navigationTitle("설정")
        }
    }

    // MARK: - 계정 섹션

    @ViewBuilder
    private var accountSection: some View {
        if appState.isLoggedIn {
            Section("BYD 계정") {
                if let vin = appState.selectedVin {
                    LabeledContent("VIN", value: vin)
                        .foregroundStyle(.secondary)
                }
                Button(role: .destructive) {
                    appState.logout()
                } label: {
                    Text("로그아웃")
                }
            }
        } else {
            Section("BYD 계정") {
                Picker("지역", selection: $region) {
                    Text("한국").tag("KR")
                    Text("유럽").tag("EU")
                    Text("일본").tag("JP")
                    Text("싱가포르").tag("SG")
                    Text("호주").tag("AU")
                }

                TextField("아이디 (이메일)", text: $username)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)

                SecureField("비밀번호", text: $password)

                if let error = appState.loginError {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                Button {
                    Task { await appState.login(username: username, password: password, region: region) }
                } label: {
                    HStack {
                        Text("로그인")
                        Spacer()
                        if appState.isLoggingIn {
                            ProgressView()
                        }
                    }
                }
                .disabled(username.isEmpty || password.isEmpty || appState.isLoggingIn)
            }
        }
    }

    // MARK: - 차량 선택 섹션 (로그인 후)

    @ViewBuilder
    private var vehicleSection: some View {
        if !appState.vehicles.isEmpty {
            Section("차량 선택") {
                ForEach(appState.vehicles) { vehicle in
                    Button {
                        appState.selectVin(vehicle.vin)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(vehicle.modelName)
                                    .foregroundStyle(.primary)
                                Text(vehicle.vin)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if appState.selectedVin == vehicle.vin {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(.blue)
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - 차종 섹션

    private var vehicleModelSection: some View {
        Section("차종 (배터리 용량 계산용)") {
            Picker("차종", selection: $vehicleModel) {
                ForEach(VehicleModel.allCases) { model in
                    Text(model.displayName).tag(model.rawValue)
                }
            }
        }
    }

    // MARK: - 전기요금 섹션

    private var electricitySection: some View {
        Section("전기요금") {
            HStack {
                Text("일반 단가")
                Spacer()
                TextField("원/kWh", value: $electricityRate, format: .number)
                    .multilineTextAlignment(.trailing)
                    .keyboardType(.decimalPad)
                    .frame(width: 60)
                Text("원/kWh").foregroundStyle(.secondary)
            }
            HStack {
                Text("심야 단가")
                Spacer()
                TextField("원/kWh", value: $nightRate, format: .number)
                    .multilineTextAlignment(.trailing)
                    .keyboardType(.decimalPad)
                    .frame(width: 60)
                Text("원/kWh").foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - 폴링 섹션

    private var pollingSection: some View {
        Section("폴링 간격") {
            Picker("간격", selection: $pollingInterval) {
                Text("5분").tag(5)
                Text("10분").tag(10)
                Text("15분").tag(15)
            }
            .pickerStyle(.segmented)
        }
    }

    // MARK: - 위치 섹션

    private var locationSection: some View {
        Section("위치") {
            Toggle("GPS 트래킹", isOn: $gpsEnabled)
        }
    }
}

// MARK: - VehicleModel

enum VehicleModel: String, CaseIterable, Identifiable {
    case atto3           = "atto3"
    case dolphinStandard = "dolphin_std"
    case dolphinExtended = "dolphin_ext"
    case seal            = "seal"
    case seaLion7        = "sealion7"
    case seaLion6        = "sealion6"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .atto3:           "아토 3"
        case .dolphinStandard: "돌핀 Standard"
        case .dolphinExtended: "돌핀 Extended"
        case .seal:            "씰"
        case .seaLion7:        "씨라이언 7"
        case .seaLion6:        "씨라이언 6"
        }
    }

    var batteryCapacityKwh: Double {
        switch self {
        case .atto3:           60.48
        case .dolphinStandard: 44.9
        case .dolphinExtended: 60.4
        case .seal:            82.56
        case .seaLion7:        82.56
        case .seaLion6:        87.23
        }
    }
}
