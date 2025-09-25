
---

# needs-spring

Apache Camel × Spring Boot で作る日経 NEEDS（JS PRICE）処理パイプラインのサンプル。
PDF → CSV 変換（`jsprice-converter`）と、CSV → TXT 変換（`csv-postprocessor`）を **Docker Compose** で連携させます。

## 構成

```
.
├─ pdf-host/            # サンプルPDFを配信する簡易HTTPサーバ (10081)
├─ jsprice-converter/   # PDFを取得してCSVへ変換する Spring Boot + Camel アプリ (10080)
├─ csv-postprocessor/   # CSVを監視しTXTへ変換する Spring Boot + Camel アプリ
├─ data/                # 共有ボリューム (出力や退避ファイルがここに生成される)
└─ docker-compose.yml
```

### データフロー

1. `jsprice-converter` が `pdf-host` から PDF をダウンロード
2. `PdfToCsvProcessor` で CSV を生成し **`/data/output`** に出力
3. `csv-postprocessor` が **`/data/output`** をポーリングし、CSV をTXTへ変換して **`/data/txt`** に出力

   * 処理済みCSVは `/data/output/.done/` へ移動

---

## クイックスタート

### 1) ビルド & 起動

```bash
# ルート (needs-spring/) で実行
docker compose up --build -d
```

起動後、ログを確認：

```bash
docker logs -f needs-spring-jsprice-converter-1
docker logs -f needs-spring-csv-postprocessor-1
```

`jsprice-converter` の起動時ログに Quartz のスケジュールが出ます（例：`Next fire date is ...`）。

### 2) 手動実行（HTTP）

`jsprice-converter` は手動APIでも起動できます。

```bash
curl -X POST http://localhost:10080/run
```

成功すると、`data/output/jsprice_YYYYMMDD.csv` が生成され、続いて `csv-postprocessor` が `data/txt/` に `.txt` を出力します。

---

## スケジューリング

### 毎週月曜 10:00（日本時間）

`jsprice-converter` には Quartz ルートがあり、**毎週月曜 10:00 JST** に自動実行されます。

* ルート: `WeeklySchedulerRoute`
* エンドポイント: `quartz://weekly/monday10?cron=0+0+10+?+*+MON&trigger.timeZone=Asia/Tokyo`
* 実行内容: `direct:runJob` を呼び出し、PDF→CSV を実施

#### デバッグ用：毎分トリガ（任意）

環境変数 `APP_DEBUG_EVERYMINUTE=true` を与えると、別ルート（`EveryMinuteDebugRoute`）が有効になり**毎分**実行されます。
`docker-compose.yml` の `jsprice-converter` に以下の環境変数を設定済みです（必要に応じて外してください）。

```yaml
environment:
  - APP_DEBUG_EVERYMINUTE=true
```

---

## `csv-postprocessor` の動作

* 監視ディレクトリ: `/data/output`
* 出力ディレクトリ: `/data/txt`
* ポーリング方式: Camel file コンポーネントの**定期ポーリング**（デフォルト約 500ms）
* 代表ルート: `csv-to-txt`

  * `readLock=changed` で書き込み完了まで待機
  * `idempotent=true` で同一ファイルの二重処理を防止
  * 処理後は `move=.done/${file:name}` で入力側を退避

### 区切り文字の変換（任意）

`application.yml` の `app.replaceCommaWithTab` を `true` にすると、CSV のカンマをタブへ置換して出力します。

```yaml
app:
  inDir: /data/output
  outDir: /data/txt
  replaceCommaWithTab: true
camel:
  springboot:
    main-run-controller: true  # ← サービス常駐のため必須
```

> **常駐設定について**
> `csv-postprocessor` は Web を持たないため、`camel.springboot.main-run-controller=true` を必ず有効化してください。これが無いと起動直後に CamelContext が終了してしまいます。

---

## 設定

### docker-compose（抜粋）

```yaml
services:
  pdf-host:
    build: { context: ./pdf-host, dockerfile: Dockerfile }
    ports: ["10081:10081"]
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:10081/jsprice/sample"]
      interval: 10s
      timeout: 5s
      retries: 10

  jsprice-converter:
    build: { context: ./jsprice-converter, dockerfile: Dockerfile }
    ports: ["10080:10080"]
    volumes: ["./data:/data"]
    environment:
      - SPRING_APPLICATION_JSON={
          "app":{"sourceUrl":"http://pdf-host:10081/jsprice/sample",
                 "output":{"dir":"/data/output","filename":"jsprice_20250630.csv"}}
        }
      - APP_DEBUG_EVERYMINUTE=true
    depends_on:
      pdf-host: { condition: service_healthy }

  csv-postprocessor:
    build: { context: ./csv-postprocessor, dockerfile: Dockerfile }
    volumes: ["./data:/data"]
    environment:
      - SPRING_PROFILES_ACTIVE=prod   # 必要なら
    depends_on:
      - jsprice-converter
```

---

## API

### `POST /run`（`jsprice-converter`）

* 説明: 即時で PDF→CSV のジョブを実行
* レスポンス: 実行ログ / ファイル出力は `data/output/` を確認

---

## ビルド & テスト（ローカル）

### Maven

```bash
# ルート（needs-spring/）で実行
mvn -q -DskipTests package

# テスト実行
mvn -q -DskipTests=false test
```

**テスト内容:**

* `PdfToCsvProcessorTest` … PDF からの表抽出の単体テスト
* ルート結線テスト（`RoutesWiringTest`, `SchedulerRouteTest`）

  * Camel の `AdviceWith` と `camel-test-spring-junit5` を使用
  * 外部I/O（HTTP/file）を `mock:` に差し替えて動作検証

---

## トラブルシュート

* **本番起動で `MockComponent` の `ClassNotFoundException`**
  → *原因*: `camel-mock` / `camel-dataset` などの **テスト用依存**が実行イメージに混入
  → *対策*: POM のテスト依存は必ず `<scope>test</scope>` を付ける。`docker compose up --build` で再ビルド。

* **`csv-postprocessor` がすぐ終了する**
  → `camel.springboot.main-run-controller=true` が無効。`application.yml` に追加のうえ再ビルド。

* **PDF が取得できない / 404**
  → `pdf-host` のヘルスチェックを確認。`docker logs -f needs-spring-pdf-host-1` で疎通とパス `/jsprice/sample` を確認。

---

## カスタマイズ

* **スケジュール変更**
  `WeeklySchedulerRoute` の `cron` を変更。JST のまま運用する場合は `trigger.timeZone=Asia/Tokyo` を維持。

* **出力先やファイル名**
  `SPRING_APPLICATION_JSON` の `app.output.dir` / `filename` を変更。
  `csv-postprocessor` 側は `application.yml` の `app.inDir` / `outDir` を合わせて変更。

* **ポーリング間隔**（`csv-postprocessor`）
  file コンポーネント URI に `&delay=5000&initialDelay=2000&readLockMinAge=1000` などを追加して調整。


---

### 変更提案の要点

* 2つのサービスの役割とデータフローを明確化
* 手動APIと定時実行の両方をすぐ試せる手順
* `csv-postprocessor` が落ちる問題の恒久対策（`main-run-controller=true`）を README に明記
* デバッグ用の毎分トリガの切り替えをドキュメント化
* よくある罠（Mock依存の混入）と対処を追記

---
