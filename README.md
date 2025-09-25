

---

# needs-spring

**日経 NEEDS JS Price（サンプルPDF）を取得→CSVに変換**する PoC。
アプリは 2 サービス構成です。

* **pdf-host** : サンプル PDF を配信（`http://localhost:10080/jsprice/sample`）
* **jsprice-converter** : PDF を取得し、本文から「銘柄名 / 償還日 / 表面利率 / 債券標準価格」を抽出して CSV を生成

Spring Boot + Apache Camel（HTTP 取得＆ファイル出力）。OpenCSV / PDFBox を使用。

---

## 目次

* [ディレクトリ構成](#ディレクトリ構成)
* [前提](#前提)
* [クイックスタート（Docker Compose）](#クイックスタートdocker-compose)
* [ローカル起動（Docker 不使用）](#ローカル起動docker-不使用)
* [エンドポイント](#エンドポイント)
* [出力CSVの仕様](#出力csvの仕様)
* [設定方法](#設定方法)
* [テスト](#テスト)
* [トラブルシュート](#トラブルシュート)
* [今後の拡張案](#今後の拡張案)
* [ライセンス](#ライセンス)

---

## ディレクトリ構成

```
needs-spring/
├── docker-compose.yml
├── data/                     # 生成CSV永続化（コンテナ起動時に ./data を /data にマウント）
│   └── output/               # CSV 出力先（例: jsprice_20250630.csv）
├── jsprice-converter/        # 変換アプリ（Spring Boot + Camel）
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
└── pdf-host/                 # PDF 配信アプリ（Spring Boot）
    ├── Dockerfile
    ├── pom.xml
    └── src/
        └── main/resources/sample/jsprice_01_202506.pdf  # ← サンプルPDF
```

> **注意**: `pdf-host/src/main/resources/sample/jsprice_01_202506.pdf` がないと PDF が配信できません。

---

## 前提

* Docker / Docker Compose が利用可能
* JDK 17+ / Maven 3.9+（ローカル実行・開発時）
* ポート未使用: `8080`（converter）, `10080`（pdf-host）

---

## クイックスタート（Docker Compose）

1. （初回のみ）`pdf-host/src/main/resources/sample/` にサンプル PDF を配置
   ファイル名: `jsprice_01_202506.pdf`

2. ビルド & 起動

   ```bash
   docker compose up --build -d
   ```

3. 動作確認（PDF 配信）

   ```bash
   curl -v http://localhost:10080/jsprice/sample -o /dev/null
   # HTTP/1.1 200 & Content-Type: application/pdf が返ればOK
   ```

4. 変換トリガ

   ```bash
   curl -X POST http://localhost:8080/run
   # → "OK" が返る
   ```

5. 出力確認

   ```bash
   cat ./data/output/jsprice_20250630.csv
   ```

> Compose 内ではサービス名で相互接続します。converter → pdf-host への取得 URL は
> `http://pdf-host:10080/jsprice/sample` です（`docker-compose.yml` の `SPRING_APPLICATION_JSON` で設定）。

---

## ローカル起動（Docker 不使用）

### A. pdf-host（10080）を起動

```bash
cd pdf-host
mvn -q -DskipTests package
java -jar target/pdf-host-0.0.1-SNAPSHOT.jar
# http://localhost:10080/jsprice/sample でPDFが返ることを確認
```

### B. jsprice-converter（8080）を起動

`jsprice-converter/src/main/resources/application.yml` の `app.sourceUrl` を `http://localhost:10080/jsprice/sample` にしてから:

```bash
cd jsprice-converter
mvn -q -DskipTests package
java -jar target/jsprice-converter-0.0.1-SNAPSHOT.jar
```

### C. 変換トリガ

```bash
curl -X POST http://localhost:8080/run
cat ./data/output/jsprice_20250630.csv
```

---

## エンドポイント

### pdf-host

* `GET /jsprice/sample`
  クラスパス上の `sample/jsprice_01_202506.pdf` を返却（`application/pdf`）

### jsprice-converter

* `POST /run`
  `app.sourceUrl` から PDF を取得 → 解析 → CSV を `/data/output`（コンテナ）へ保存
  成功時: `"OK"` を返却

---

## 出力CSVの仕様

* ヘッダ:

  ```
  as_of_date,brand,maturity_date,coupon_pct,price_jpy
  ```
* 行例:

  ```
  2025-06-30,第１０回 利付国債（３０年）,2033-03-20,1.1,99.2936
  ```
* 文字コード: UTF-8（改行 LF）
* 数値: カンマ削除・小数点維持（`BigDecimal.stripTrailingZeros()`）

---

## 設定方法

### 環境変数で上書き（推奨）

`docker-compose.yml` にて `SPRING_APPLICATION_JSON` で上書きしています。

```json
{
  "app": {
    "sourceUrl": "http://pdf-host:10080/jsprice/sample",
    "output": { "dir": "/data/output", "filename": "jsprice_20250630.csv" }
  }
}
```

### `application.yml` で指定（ローカル起動時など）

`jsprice-converter/src/main/resources/application.yml`

```yaml
server:
  port: 8080

app:
  sourceUrl: "http://localhost:10080/jsprice/sample"
  output:
    dir: "data/output"
    filename: "jsprice_20250630.csv"
```

> Excel 連携のため Shift\_JIS で出力したい場合は Camel の file エンドポイントに
> `&charset=MS932` を付与するなどで対応可能です。

---

## テスト

ユニットテスト（PDFBox を用いた ASCII 簡易PDFで検証）:

```bash
cd jsprice-converter
mvn -DskipTests=false -Dtest=PdfToCsvProcessorTest test
```

---

## トラブルシュート

* **`/run` が 500 を返す**

  * `docker compose logs jsprice-converter` でスタックトレースを確認
  * `pdf-host` が起動していない / `sourceUrl` が不正 / サンプル PDF 未配置 等
* **Compose が変換側を待ち続ける**

  * `pdf-host` のヘルスチェックが失敗している可能性
    → runtime イメージに `curl` を入れるか、`healthcheck` を外す
* **CSV が出力されない**

  * 出力先ディレクトリの権限 / マウント設定を確認
    （ホスト `./data` がコンテナ `/data` にマウントされます）

---

## 今後の拡張案

* 複数 PDF バッチ処理（ディレクトリ監視 / S3 取り込み）
* レイアウト崩れ時のフォールバック（Tabula 連携など）
* 解析ロジックの高度化（列推定 / ノイズ行除外 / 全角→半角 正規化）
* スケジューラ起動（cron 風 / Camel Timer）
* Basic 認証や署名検証（pdf-host 側）

---

