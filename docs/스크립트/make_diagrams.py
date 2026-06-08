import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import numpy as np

# ── 공통 헬퍼 ───────────────────────────────────────────────────────────────
def box(ax, x, y, w, h, text, bg, fg='white', fontsize=11, bold=False, radius=0.04):
    fancy = FancyBboxPatch((x - w/2, y - h/2), w, h,
                           boxstyle=f"round,pad={radius}",
                           facecolor=bg, edgecolor='white', linewidth=2, zorder=3)
    ax.add_patch(fancy)
    weight = 'bold' if bold else 'normal'
    ax.text(x, y, text, ha='center', va='center', fontsize=fontsize,
            color=fg, weight=weight, zorder=4,
            fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

def arrow(ax, x1, y1, x2, y2, color='#888888', style='->', lw=2):
    ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle=style, color=color,
                                lw=lw, connectionstyle='arc3,rad=0'))


# ════════════════════════════════════════════════════════════════════════════
# 그림 1: Before / After 비교 (좌우 분할)
# ════════════════════════════════════════════════════════════════════════════
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 9))
fig.patch.set_facecolor('#F8F9FB')
for ax in (ax1, ax2):
    ax.set_xlim(0, 10); ax.set_ylim(0, 10)
    ax.axis('off')

# ── 왼쪽: 기존 구조 ────────────────────────────────────────────────────────
ax1.set_facecolor('#FFF5F5')
ax1.add_patch(plt.Rectangle((0,0),10,10, facecolor='#FFF5F5', zorder=0))
ax1.text(5, 9.4, '❌  기존 구조', ha='center', va='center', fontsize=15,
         color='#CC3333', weight='bold',
         fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

# nodes
nodes_old = [
    (5, 8.3, '보호자가 일정 등록', '#6C757D'),
    (5, 6.9, 'AI가 단계 목록 생성\n(실행할 때마다)', '#CC3333'),
    (5, 5.5, 'AI가 단계 안내', '#CC3333'),
    (5, 4.1, 'AI가 완료 여부 판단\n(맞아? 틀려? AI 마음대로)', '#CC3333'),
    (5, 2.7, '다음 단계로 이동', '#6C757D'),
]
for x, y, t, c in nodes_old:
    box(ax1, x, y, 7, 0.9, t, c, fontsize=10)

for i in range(len(nodes_old)-1):
    _, y1, _, _ = nodes_old[i]
    _, y2, _, _ = nodes_old[i+1]
    arrow(ax1, 5, y1-0.45, 5, y2+0.45, color='#CC3333')

# 문제 설명 박스
ax1.add_patch(FancyBboxPatch((0.5, 0.3), 9, 1.8,
    boxstyle='round,pad=0.05', facecolor='#FFE5E5', edgecolor='#CC3333', linewidth=1.5, zorder=3))
problems = ['• 실행할 때마다 단계가 달라짐  →  사용자 혼란',
            '• AI가 완료를 잘못 판단하는 경우 많음',
            '• 보호자가 단계 내용을 확인·수정할 수 없음']
for i, t in enumerate(problems):
    ax1.text(1, 1.9 - i*0.5, t, fontsize=9.5, color='#991111', va='center', zorder=4,
             fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

# ── 오른쪽: 현재 구조 ────────────────────────────────────────────────────────
ax2.set_facecolor('#F4FBF6')
ax2.add_patch(plt.Rectangle((0,0),10,10, facecolor='#F4FBF6', zorder=0))
ax2.text(5, 9.4, '✅  현재 구조', ha='center', va='center', fontsize=15,
         color='#1A7A3C', weight='bold',
         fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

# 등록 시점
box(ax2, 5, 8.5, 9, 0.6, '▶  일정 등록 시점  (보호자)', '#1A5F9E', fontsize=10, bold=True)
nodes_reg = [
    (2.5, 7.4, '보호자가\n일정 등록', '#3A7BD5'),
    (5.5, 7.4, 'AI가 단계 생성\n(딱 한 번만)', '#1A7A3C'),
    (8.5, 7.4, '보호자가 확인·\n편집 후 저장', '#3A7BD5'),
]
for x, y, t, c in nodes_reg:
    box(ax2, x, y, 2.6, 1.0, t, c, fontsize=9.5)
arrow(ax2, 3.8, 7.4, 4.2, 7.4, color='#1A5F9E')
arrow(ax2, 6.8, 7.4, 7.2, 7.4, color='#1A5F9E')

# 실행 시점
box(ax2, 5, 6.4, 9, 0.6, '▶  일정 실행 시점  (사용자 + 똘똘이)', '#5B2D8E', fontsize=10, bold=True)
nodes_run = [
    (2.0, 5.3, '알람 → 저장된\n단계 불러오기', '#7B52AE'),
    (5.0, 5.3, '똘똘이가\n단계 안내', '#7B52AE'),
    (8.0, 5.3, '사용자 음성\n응답', '#7B52AE'),
]
for x, y, t, c in nodes_run:
    box(ax2, x, y, 2.6, 1.0, t, c, fontsize=9.5)
arrow(ax2, 3.3, 5.3, 3.7, 5.3, color='#5B2D8E')
arrow(ax2, 6.3, 5.3, 6.7, 5.3, color='#5B2D8E')

# 완료 판단 분기
box(ax2, 5, 3.8, 9, 0.6, '"했어요" / "응" / "끝"  →  앱이 직접 완료 판단', '#FF8C00', fontsize=10, bold=True)
arrow(ax2, 8.0, 4.8, 8.0, 4.1, color='#5B2D8E')
arrow(ax2, 6.4, 3.8, 5.6, 3.8, color='#FF8C00')  # dummy

box(ax2, 2.5, 3.0, 3.5, 0.7, '✅ 다음 단계로', '#1A7A3C', fontsize=10)
box(ax2, 7.5, 3.0, 3.5, 0.7, '❓ 질문 → 똘똘이 답변', '#3A7BD5', fontsize=10)
arrow(ax2, 3.5, 3.8, 2.8, 3.35, color='#1A7A3C')
arrow(ax2, 6.5, 3.8, 7.2, 3.35, color='#3A7BD5')

# 효과
ax2.add_patch(FancyBboxPatch((0.5, 0.3), 9, 1.8,
    boxstyle='round,pad=0.05', facecolor='#E5F5EA', edgecolor='#1A7A3C', linewidth=1.5, zorder=3))
goods = ['• 단계가 항상 똑같음  →  사용자가 혼란 없이 수행 가능',
         '• 보호자가 단계 내용 확인·수정 가능',
         '• 앱이 완료를 직접 판단  →  AI 오판 없음']
for i, t in enumerate(goods):
    ax2.text(1, 1.9 - i*0.5, t, fontsize=9.5, color='#1A4A28', va='center', zorder=4,
             fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

plt.tight_layout(pad=0.5)
plt.savefig(r'C:\Users\User\Desktop\diagram_before_after.png', dpi=150, bbox_inches='tight',
            facecolor=fig.get_facecolor())
plt.close()
print('diagram 1 done')


# ════════════════════════════════════════════════════════════════════════════
# 그림 2: 똘똘이 대화 판단 로직 (4분류)
# ════════════════════════════════════════════════════════════════════════════
fig2, ax = plt.subplots(figsize=(14, 7))
fig2.patch.set_facecolor('#F8F9FB')
ax.set_xlim(0, 14); ax.set_ylim(0, 7)
ax.axis('off')
ax.set_facecolor('#F8F9FB')

ax.text(7, 6.6, '똘똘이 대화 판단 방식', ha='center', fontsize=16, weight='bold',
        color='#1A1A2E',
        fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

# 사용자 발화 (입력)
box(ax, 7, 5.7, 5, 0.75, '사용자 발화', '#1A5F9E', fontsize=12, bold=True)

# 4개 분류 박스 (y=4.1)
categories = [
    (1.5,  '"다 했어"\n"응" "끝났어"',    '①  완료 신호',      '#1A7A3C'),
    (5.0,  '"어떻게 해?"\n"이게 뭐야?"',  '②  질문',           '#3A7BD5'),
    (8.5,  '"심심해"\n"엄마 언제 와?"',   '③  관련 없는 말',   '#FF8C00'),
    (12.0, '"못 하겠어"\n"힘들어"',        '④  힘들어함',       '#9B59B6'),
]
for x, trigger, label, color in categories:
    box(ax, x, 4.1, 2.6, 1.1, f'{label}\n{trigger}', color, fontsize=9.5)
    arrow(ax, 7, 5.32, x, 4.65, color=color)

# 반응 박스 (y=2.4)
responses = [
    (1.5,  '칭찬하고\n다음 단계로!',     '#1A7A3C'),
    (5.0,  '쉽게 설명하고\n다시 단계 유도', '#3A7BD5'),
    (8.5,  '한 번 받아주고\n부드럽게 유도', '#FF8C00'),
    (12.0, '재촉 없이 응원\n더 쉽게 안내', '#9B59B6'),
]
for (x, trigger, label, color), (rx, resp, rc) in zip(categories, responses):
    box(ax, rx, 2.4, 2.6, 1.0, resp, rc, fontsize=9.5)
    arrow(ax, x, 3.55, rx, 2.9, color=rc)

# 하단 룰 설명
ax.add_patch(FancyBboxPatch((1.0, 0.2), 12, 0.9,
    boxstyle='round,pad=0.05', facecolor='#EEF2FF', edgecolor='#1A5F9E', linewidth=1.5, zorder=3))
ax.text(7, 0.65, '공통 규칙:  한 문장 15자 이내  |  어려운 단어 금지  |  같은 질문을 반복해도 짜증 없이 친절하게',
        ha='center', va='center', fontsize=10, color='#1A1A2E', zorder=4,
        fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

plt.tight_layout(pad=0.3)
plt.savefig(r'C:\Users\User\Desktop\diagram_4class.png', dpi=150, bbox_inches='tight',
            facecolor=fig2.get_facecolor())
plt.close()
print('diagram 2 done')


# ════════════════════════════════════════════════════════════════════════════
# 그림 3: 테스트 커버리지 바 차트
# ════════════════════════════════════════════════════════════════════════════
fig3, ax = plt.subplots(figsize=(10, 4.5))
fig3.patch.set_facecolor('#F8F9FB')
ax.set_facecolor('#F8F9FB')

labels = ['AI 파이프라인\n(핵심 로직)', '보호자 도메인', '일정 도메인', '서버 전체\n(평균)']
values = [54, 53, 30, 10]
colors = ['#1A7A3C', '#3A7BD5', '#FF8C00', '#6C757D']

bars = ax.barh(labels, values, color=colors, height=0.55, edgecolor='white', linewidth=1.5)
ax.set_xlim(0, 100)
ax.set_xlabel('코드 커버리지 (%)', fontsize=11,
              fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))
ax.set_title('단위 테스트 코드 커버리지 (JaCoCo)', fontsize=13, weight='bold', pad=12,
             fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

for bar, val in zip(bars, values):
    ax.text(val + 1.5, bar.get_y() + bar.get_height()/2,
            f'{val}%', va='center', fontsize=12, weight='bold',
            color='#333333',
            fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

# 목표선
ax.axvline(x=40, color='#CC3333', linestyle='--', linewidth=1.5, alpha=0.7)
ax.text(41, -0.7, '목표 40%\n(다음 주)', fontsize=9, color='#CC3333',
        fontproperties=matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

for spine in ['top','right']:
    ax.spines[spine].set_visible(False)
ax.tick_params(axis='y', labelsize=10.5)
for label in ax.get_yticklabels():
    label.set_fontproperties(matplotlib.font_manager.FontProperties(family='Malgun Gothic'))

plt.tight_layout()
plt.savefig(r'C:\Users\User\Desktop\diagram_coverage.png', dpi=150, bbox_inches='tight',
            facecolor=fig3.get_facecolor())
plt.close()
print('diagram 3 done')
