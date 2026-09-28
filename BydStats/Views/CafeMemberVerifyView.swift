import SwiftUI

private let regionOptions  = ["서울", "경기", "인천", "강원", "충북", "충남", "세종", "대전", "대구", "경북", "울산", "부산", "경남", "전북", "전남", "광주", "제주", "기타"]
private let carModelOptions = ["Atto 3", "Seal", "Dolphin", "Sealion 7", "기타"]

struct CafeMemberVerifyView: View {
    @Environment(AppState.self) private var appState

    @State private var region   = ""
    @State private var nickname = ""
    @State private var carModel = ""
    @State private var isVerifying = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("BYD 써드파티연구소 카페 닉네임으로 회원 여부를 확인합니다.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .listRowBackground(Color.clear)
                        .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                }

                Section("카페 닉네임") {
                    Picker("지역", selection: $region) {
                        Text("지역 선택").tag("")
                        ForEach(regionOptions, id: \.self) { Text($0).tag($0) }
                    }

                    TextField("닉네임", text: $nickname)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)

                    Picker("차종", selection: $carModel) {
                        Text("차종 선택").tag("")
                        ForEach(carModelOptions, id: \.self) { Text($0).tag($0) }
                    }
                } footer: {
                    Text("카페 닉네임 형식: 지역ll닉네임ll차종")
                        .font(.caption)
                }

                if let error = errorMessage {
                    Section {
                        Text(error)
                            .foregroundStyle(.red)
                            .font(.caption)
                    }
                }

                Section {
                    Button {
                        Task { await verify() }
                    } label: {
                        HStack {
                            Spacer()
                            if isVerifying {
                                ProgressView()
                            } else {
                                Text("인증하기")
                                    .fontWeight(.semibold)
                            }
                            Spacer()
                        }
                    }
                    .disabled(region.isEmpty || nickname.trimmingCharacters(in: .whitespaces).isEmpty || carModel.isEmpty || isVerifying)
                }
            }
            .navigationTitle("카페 회원 인증")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("로그아웃") {
                        appState.logout()
                    }
                    .foregroundStyle(.secondary)
                }
            }
            .onAppear { prefill() }
        }
    }

    // 이전에 저장된 닉네임이 있으면 파싱해서 채움
    private func prefill() {
        guard let saved = CafeMemberService.savedNick else { return }
        let parts = saved.components(separatedBy: "ll")
        guard parts.count == 3 else { return }
        region   = parts[0]
        nickname = parts[1]
        carModel = parts[2]
    }

    private func verify() async {
        isVerifying = true
        errorMessage = nil
        defer { isVerifying = false }

        let nick = nickname.trimmingCharacters(in: .whitespaces)

        do {
            let status = try await CafeMemberService.verify(
                region: region, nickname: nick, carModel: carModel
            )
            appState.applyCafeVerifyResult(status)
        } catch {
            errorMessage = "인증 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        }
    }
}
