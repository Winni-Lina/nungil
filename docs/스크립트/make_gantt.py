import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
from matplotlib.patches import FancyBboxPatch

FP  = fm.FontProperties(family='Malgun Gothic')
FPB = fm.FontProperties(family='Malgun Gothic', weight='bold')

fig, ax = plt.subplots(figsize=(16, 7.5))
fig.patch.set_facecolor('#F8F9FB')
ax.set_facecolor('#F8F9FB')

tasks = [
    ('기반 구축 (DB·API·앱 연동)',           1,  7,  '#6C757D', True),
    ('AI 구조 개편 (프롬프트·단계 분리)',    3,  8,  '#1A5F9E', True),
    ('전체 서비스 통합',                     5,  8,  '#3A7BD5', True),
    ('단위+E2E 테스트 30개',                 6,  8,  '#1A7A3C', True),
    ('Gemini 결제 전환',                     7,  8,  '#1A7A3C', True),
    ('AI 응답 튜닝',                         9,  11, '#9B59B6', False),
    ('보호자 앱 UI 개편',                    9,  12, '#E67E22', False),
    ('실기기 전체 테스트',                   11, 13, '#E74C3C', False),
    ('보호자 앱 도움말 추가',                12, 14, '#16A085', False),  # 신규
    ('버그 수정 · 최종 점검',               13, 14, '#C0392B', False),
    ('발표 자료 준비',                       12, 15, '#2C3E50', False),
]

for i, (name, start, end, color, done) in enumerate(tasks):
    y = len(tasks) - 1 - i
    ax.barh(y, end-start, left=start, height=0.55,
            color=color, alpha=1.0, edgecolor='white', linewidth=1.5, zorder=3)
    label = '완료' if done else '예정'
    ax.text(start+(end-start)/2, y, label, ha='center', va='center',
            fontsize=8.5, color='white', fontproperties=FPB, zorder=4)

ax.set_yticks(range(len(tasks)))
ax.set_yticklabels([t[0] for t in reversed(tasks)], fontproperties=FP, fontsize=10)
ax.set_xlim(0.5, 15.5)
ax.set_xticks(range(1,16))
ax.set_xticklabels([f'6/{d}' for d in range(1,16)], fontproperties=FP, fontsize=9)

ax.axvline(x=8,  color='#CC3333',   lw=2.5, linestyle='--', zorder=5)
ax.axvline(x=15, color='#1A1A2E',   lw=2.5, linestyle='-',  zorder=5)
ax.text(8,  len(tasks)-0.1, '  오늘(6/8)', color='#CC3333',  fontsize=9, va='top', fontproperties=FPB)
ax.text(14.7, len(tasks)-0.1, '마감\n(6/15)', color='#1A1A2E', fontsize=9, va='top', ha='right', fontproperties=FPB)
ax.axvspan(0.5, 8,    alpha=0.06, color='#1A7A3C', zorder=1)
ax.axvspan(8,   15.5, alpha=0.06, color='#E67E22', zorder=1)
ax.text(4.2,  -0.85, '개발 완료 구간', color='#1A7A3C', fontsize=9.5, fontproperties=FPB, ha='center')
ax.text(11.5, -0.85, '마무리 구간',   color='#E67E22', fontsize=9.5, fontproperties=FPB, ha='center')

ax.set_title('눈길 프로젝트 개발 일정  (2026. 06)', fontsize=13, weight='bold', pad=12, fontproperties=FPB)
ax.set_xlabel('날짜', fontsize=10, fontproperties=FP)
for spine in ['top','right']: ax.spines[spine].set_visible(False)
ax.tick_params(axis='y', length=0)
ax.grid(axis='x', linestyle=':', alpha=0.4, zorder=0)

plt.tight_layout()
plt.savefig(r'C:\Users\User\Desktop\diagram_gantt.png', dpi=150,
            bbox_inches='tight', facecolor=fig.get_facecolor())
plt.close()
print('gantt done')
