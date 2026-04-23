# 起動手順

## バックエンド（Spring Boot）

MavenはJava 21で実行してください（Java 25はLombokと非互換のため）。

```bash
cd backend

# ビルド & 起動
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  mvn spring-boot:run
```

サーバーは http://localhost:8080 で起動します。

## フロントエンド

`frontend/index.html` をブラウザで直接開くか、任意の静的サーバーで配信してください。

```bash
# 例: Python の簡易サーバー
cd frontend
python3 -m http.server 3000
# → http://localhost:3000 でアクセス
```

## 動作確認（curl）

```bash
# ヘルスチェック
curl http://localhost:8080/api/health

# HTML直接診断
curl -X POST http://localhost:8080/api/diagnose \
  -H "Content-Type: application/json" \
  -d '{"html":"<form><input type=\"email\" required><button>送信</button></form>"}'
```
