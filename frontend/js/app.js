/* ==========================================================
   フォームUXスコア診断ツール - フロントエンドスクリプト
========================================================== */

const API_BASE = 'http://localhost:8081/api';

// ---- DOM参照 ----
const tabs        = document.querySelectorAll('.tab');
const tabContents = document.querySelectorAll('.tab-content');
const diagnoseBtn = document.getElementById('diagnose-btn');
const pdfBtn      = document.getElementById('pdf-btn');
const loading     = document.getElementById('loading');
const errorBanner = document.getElementById('error-message');
const resultSec   = document.getElementById('result-section');

let currentReportId = null;

// ---- タブ切り替え ----
tabs.forEach(tab => {
  tab.addEventListener('click', () => {
    tabs.forEach(t => t.classList.remove('active'));
    tabContents.forEach(c => c.classList.remove('active'));
    tab.classList.add('active');
    document.getElementById(tab.dataset.target).classList.add('active');
  });
});

// ---- 診断実行 ----
diagnoseBtn.addEventListener('click', async () => {
  const activeTab = document.querySelector('.tab.active').dataset.target;
  let requestBody = {};

  if (activeTab === 'tab-url') {
    const url = document.getElementById('url-input').value.trim();
    if (!url) { showError('URLを入力してください。'); return; }
    requestBody = { url };
  } else {
    const html = document.getElementById('html-input').value.trim();
    if (!html) { showError('HTMLを貼り付けてください。'); return; }
    requestBody = { html };
  }

  setLoading(true);
  hideError();
  hideResult();

  try {
    const res = await fetch(`${API_BASE}/diagnose`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody),
    });

    const data = await res.json();

    if (!res.ok) {
      showError(data.error || '診断に失敗しました。');
      return;
    }

    renderResult(data);

  } catch (e) {
    showError('サーバーに接続できませんでした。バックエンドが起動しているか確認してください。');
    console.error(e);
  } finally {
    setLoading(false);
  }
});

// ---- PDFダウンロード ----
pdfBtn.addEventListener('click', async () => {
  if (!currentReportId) return;
  pdfBtn.disabled = true;
  pdfBtn.textContent = '生成中...';
  try {
    const res = await fetch(`${API_BASE}/report/${currentReportId}/pdf`);
    if (!res.ok) { alert('PDFの生成に失敗しました。'); return; }
    const blob = await res.blob();
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href     = url;
    a.download = `formux-report-${currentReportId}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    alert('PDFのダウンロードに失敗しました。');
    console.error(e);
  } finally {
    pdfBtn.disabled = false;
    pdfBtn.textContent = '📄 PDFレポートをダウンロード';
  }
});

// ---- 結果レンダリング ----
function renderResult(data) {
  currentReportId = data.reportId;
  // 総合スコアゲージ
  const circumference = 2 * Math.PI * 50; // r=50
  const fill = (data.overallScore / 100) * circumference;
  document.getElementById('gauge-fill').style.strokeDasharray = `${fill} ${circumference}`;
  document.getElementById('gauge-score').textContent = data.overallScore;
  document.getElementById('grade-badge').textContent  = data.overallGrade;
  document.getElementById('overall-comment').textContent = data.overallComment;

  // グレードに応じてゲージ色を変更
  const gaugeFill = document.getElementById('gauge-fill');
  gaugeFill.style.stroke = gradeColor(data.overallGrade);
  document.getElementById('grade-badge').style.background = gradeColor(data.overallGrade);

  // 項目別スコア
  const criteriaList = document.getElementById('criteria-list');
  criteriaList.innerHTML = '';
  data.criteria.forEach((c, i) => {
    criteriaList.appendChild(buildCriterionCard(c, i + 1));
  });

  // 優先改善事項
  const suggestionsList = document.getElementById('suggestions-list');
  suggestionsList.innerHTML = '';
  if (data.topSuggestions.length === 0) {
    const li = document.createElement('li');
    li.textContent = '特に大きな問題は検出されませんでした。現状を維持してください。';
    suggestionsList.appendChild(li);
  } else {
    data.topSuggestions.forEach(s => {
      const li = document.createElement('li');
      li.textContent = s;
      suggestionsList.appendChild(li);
    });
  }

  resultSec.classList.remove('hidden');
  resultSec.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function safeText(val, fallback) {
  return (val != null && String(val).trim() !== '') ? val : fallback;
}

function buildCriterionCard(c, index) {
  const rate = Math.round((c.score / c.maxScore) * 100);

  const card = document.createElement('div');
  card.className = 'criterion-card';
  card.innerHTML = `
    <div class="criterion-header">
      <div class="criterion-name">
        <span>&#x${numberToCircled(index)};</span>
        ${escHtml(c.name)}
        <span class="level-badge level-${c.level}">${levelLabel(c.level)}</span>
      </div>
      <div class="criterion-score-text">${c.score} / ${c.maxScore}点</div>
    </div>
    <div class="progress-bar-bg">
      <div class="progress-bar-fill fill-${c.level}" style="width: ${rate}%"></div>
    </div>
    <div class="criterion-detail">${escHtml(c.detail)}</div>
    <div class="ba-compare">
      <div class="ba-col-before">
        <span class="ba-tag-before">改善前</span>
        ${escHtml(safeText(c.beforeMessage, '（情報なし）'))}
      </div>
      <div class="ba-col-after">
        <span class="ba-tag-after">改善後</span>
        ${escHtml(safeText(c.suggestion, '（提案を生成できませんでした）'))}
      </div>
    </div>
  `;
  return card;
}

// ---- ユーティリティ ----
function gradeColor(grade) {
  return { A: '#16a34a', B: '#2563eb', C: '#d97706', D: '#dc2626' }[grade] || '#2563eb';
}

function levelLabel(level) {
  return { GOOD: '良好', FAIR: '要改善', POOR: '不十分' }[level] || level;
}

// 丸数字のUnicodeコードポイント（① = 2460, ...）
function numberToCircled(n) {
  return (0x245F + n).toString(16).toUpperCase();
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/\n/g, '<br>');
}

function setLoading(on) {
  loading.classList.toggle('hidden', !on);
  diagnoseBtn.disabled = on;
}

function showError(msg) {
  errorBanner.textContent = msg;
  errorBanner.classList.remove('hidden');
}

function hideError() {
  errorBanner.classList.add('hidden');
}

function hideResult() {
  resultSec.classList.add('hidden');
}
