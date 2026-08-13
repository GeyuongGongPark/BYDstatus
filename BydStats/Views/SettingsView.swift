import SwiftUI

struct SettingsView: View {
    @AppStorage("bydUsername") private var username = ""
    @AppStorage("bydPassword") private var password = ""
    @AppStorage("vehicleModel") private var vehicleModel = VehicleModel.atto3.rawValue
    @AppStorage("electricityRate") private var electricityRate = 180.0
    @AppStorage("nightRate") private var nightRate = 120.0
    @AppStorage("pollingInterval") private var pollingInterval = 5
    @AppStorage("gpsEnabled") private var gpsEnabled = true

    var body: some View {
        NavigationStack {
            Form {
                Section("BYD 계정") {
                    TextField("아이디", text: $username)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    SecureField("비밀번호", text: $password)
                }

                Section("차량 설정") {
                    Picker("차종", selection: $vehicleModel) {
                        ForEach(VehicleModel.allCases) { model in
                            Text(model.displayName).tag(model.rawValue)
                        }
                    }
                }

                Section("전기요금") {
                    HStack {
                        Text("일반 단가")
                        Spacer()
                        TextField("원/kWh", value: $electricityRate, format: .number)
                            .multilineTextAlignment(.trailing)
                            .keyboardType(.decimalPad)
                        Text("원/kWh").foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("심야 단가")
                        Spacer()
                        TextField("원/kWh", value: $nightRate, format: .number)
                            .multilineTextAlignment(.trailing)
                            .keyboardType(.decimalPad)
                        Text("원/kWh").foregroundStyle(.secondary)
                    }
                }

                Section("폴링 간격") {
                    Picker("간격", selection: $pollingInterval) {
                        Text("5분").tag(5)
                        Text("10분").tag(10)
                        Text("15분").tag(15)
                    }
                    .pickerStyle(.segmented)
                }

                Section("위치") {
                    Toggle("GPS 트래킹", isOn: $gpsEnabled)
                }
            }
            .navigationTitle("설정")
        }
    }
}

enum VehicleModel: String, CaseIterable, Identifiable {
    case atto3 = "atto3"
    case dolphinStandard = "dolphin_std"
    case dolphinExtended = "dolphin_ext"
    case seal = "seal"
    case seaLion7 = "sealion7"
    case seaLion6 = "sealion6"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .atto3: "아토 3"
        case .dolphinStandard: "돌핀 Standard"
        case .dolphinExtended: "돌핀 Extended"
        case .seal: "씰"
        case .seaLion7: "씨라이언 7"
        case .seaLion6: "씨라이언 6"
        }
    }

    var batteryCapacityKwh: Double {
        switch self {
        case .atto3: 60.48
        case .dolphinStandard: 44.9
        case .dolphinExtended: 60.4
        case .seal: 82.56
        case .seaLion7: 82.56
        case .seaLion6: 87.23
        }
    }
}
