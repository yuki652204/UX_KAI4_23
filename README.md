# csv-batch

CSVファイルを読み込んでDBに保存するSpring Bootバッチアプリケーションです。
管理画面からボタン一つでバッチを起動できます。

## 概要

`input.csv` を読み込み、バリデーションを行った上でH2データベースに保存します。
処理結果を `output.csv` に書き出し、管理画面でDB登録済みユーザーを確認できます。

## 使用技術

| 技術 | バージョン | 用途 |
|------|-----------|------|
| Java | 17 | 開発言語 |
| Spring Boot | 3.3.5 | アプリケーションフレームワーク |
| Spring Batch | - | バッチ処理（Job/Step/Reader/Processor/Writer） |
| Spring MVC | - | 管理画面のコントローラー |
| Thymeleaf | - | 管理画面のテンプレートエンジン |
| Spring Data JPA | - | DB操作 |
| H2 Database | - | 組み込みDB（開発・テスト用） |
| OpenCSV | 5.9 | CSV読み書き |
| JUnit5 | - | テスト |

## 機能

- CSVファイルの読み込み
- バリデーションチェック（name・email・age）
- H2データベースへの保存
- 処理結果をoutput.csvに書き出し
- 管理画面からバッチを手動起動
- 管理画面でDB登録済みユーザーを一覧表示

## バリデーションルール

| 項目 | ルール |
|------|--------|
| name | 空欄NG |
| email | 空欄NG・@を含む |
| age | 空欄NG・数字のみ |

## プロジェクト構成

```
src/
├── main/
│   ├── java/com/example/demo/
│   │   ├── batch/
│   │   │   ├── AdminController.java      # 管理画面・バッチ起動API
│   │   │   ├── BatchConfig.java          # Spring Batch設定（Job/Step）
│   │   │   └── CsvImportBatch.java       # シンプル版バッチ（無効化済み）
│   │   ├── model/
│   │   │   ├── UserRecord.java           # エンティティ
│   │   │   ├── UserRecordRepository.java # DBアクセス
│   │   │   └── UserRecordValidator.java  # バリデーション
│   │   └── DemoApplication.java
│   └── resources/
│       ├── templates/
│       │   └── admin.html                # 管理画面（Thymeleaf）
│       ├── application.properties
│       ├── input.csv                     # 入力ファイル
│       └── output.csv                    # 出力ファイル（自動生成）
└── test/
    └── java/com/example/demo/model/
        └── UserRecordValidatorTest.java  # テスト（5件）
```

## Spring Batchの構成

```
Job（csvImportJob）
　└── Step（csvImportStep）
　　　├── Reader　　：input.csvを1行ずつ読み込む（FlatFileItemReader）
　　　├── Processor：バリデーション → UserRecordに変換（ItemProcessor）
　　　└── Writer　　：DBに保存（JpaItemWriter）
```

## 入力ファイル形式（input.csv）

```csv
name,email,age
山田太郎,taro@example.com,28
鈴木花子,hanako@example.com,34
```

## 出力ファイル形式（output.csv）

```csv
"name","email","age","status"
"山田太郎","taro@example.com","28","SUCCESS"
"佐藤次郎","notanemail","25","ERROR"
```

## 実行方法

Spring Tools for Eclipse でプロジェクトを右クリック → `Run As` → `Spring Boot App`

起動後、ブラウザで管理画面にアクセス：

```
http://localhost:8080/admin
```

管理画面から「▶ CSVインポートバッチを起動」ボタンを押すとバッチが実行されます。

## テスト実行

`UserRecordValidatorTest.java` を右クリック → `Run As` → `JUnit Test`

テスト項目：
- 正常データは `true` を返す
- nameが空の場合は `false` を返す
- emailにアットマークがない場合は `false` を返す
- ageが数字でない場合は `false` を返す
- 配列が短い場合は `false` を返す
