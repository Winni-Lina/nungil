import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch
import matplotlib.font_manager as fm

FP  = fm.FontProperties(family='Malgun Gothic')
FPB = fm.FontProperties(family='Malgun Gothic', weight='bold')

fig, ax = plt.subplots(figsize=(16, 10))
fig.patch.set_facecolor('#F8F9FB')
ax.set_xlim(0, 16); ax.set_ylim(0, 10)
ax.axis('off')
ax.set_facecolor('#F8F9FB')

def draw_table(ax, x, y, title, fields, color, w=3.2, row_h=0.38):
    total_h = row_h * (len(fields) + 1)
    # 테이블 외곽
    ax.add_patch(FancyBboxPatch((x, y - total_h + row_h), w, total_h,
        boxstyle='round,pad=0.04', facecolor='white',
        edgecolor=color, linewidth=2, zorder=3))
    # 헤더
    ax.add_patch(FancyBboxPatch((x, y - total_h + row_h + total_h - row_h), w, row_h,
        boxstyle='square,pad=0', facecolor=color, edgecolor=color, linewidth=0, zorder=4))
    ax.text(x + w/2, y + row_h*0.5, title, ha='center', va='center',
            color='white', fontproperties=FPB, fontsize=11, zorder=5)
    # 행
    for i, (fname, ftype, pk, fk) in enumerate(fields):
        fy = y - i * row_h
        if i % 2 == 1:
            ax.add_patch(plt.Rectangle((x, fy - row_h), w, row_h,
                facecolor='#F0F4FF', zorder=3))
        prefix = ''
        if pk:  prefix = 'PK  '
        elif fk: prefix = 'FK  '
        ax.text(x + 0.12, fy - row_h*0.5, prefix + fname,
                ha='left', va='center', fontproperties=FP, fontsize=9.5,
                color='#CC3333' if pk else ('#1A5F9E' if fk else '#222'), zorder=5)
        ax.text(x + w - 0.12, fy - row_h*0.5, ftype,
                ha='right', va='center', fontproperties=FP, fontsize=8.5,
                color='#888', zorder=5)
    return x + w/2, y + row_h/2, x + w/2, y - total_h + row_h + row_h/2

def arrow(ax, x1, y1, x2, y2, color='#888', label='', lw=1.8):
    ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
        arrowprops=dict(arrowstyle='->', color=color, lw=lw,
                        connectionstyle='arc3,rad=0.08'))
    if label:
        mx, my = (x1+x2)/2, (y1+y2)/2
        ax.text(mx, my+0.1, label, ha='center', fontproperties=FP,
                fontsize=8.5, color=color)

# ── 테이블 정의 ─────────────────────────────────────────────────────────────
# GUARDIAN  (좌상)
G_x, G_y = 0.5, 9.2
draw_table(ax, G_x, G_y, 'GUARDIAN (보호자)', [
    ('id',       'VARCHAR2 PK', True,  False),
    ('pw',       'VARCHAR2',    False, False),
    ('email',    'VARCHAR2',    False, False),
    ('phone',    'VARCHAR2',    False, False),
    ('name',     'VARCHAR2',    False, False),
    ('fcmToken', 'VARCHAR2',    False, False),
    ('regdate',  'TIMESTAMP',   False, False),
], '#1A5F9E')

# NUNGIL_USER  (좌하)
U_x, U_y = 0.5, 4.8
draw_table(ax, U_x, U_y, 'NUNGIL_USER (사용자)', [
    ('id',          'VARCHAR2 PK/FK', True,  True),
    ('idx',         'NUMBER PK',      True,  False),
    ('userName',    'VARCHAR2',       False, False),
    ('userPhone',   'VARCHAR2',       False, False),
    ('specialNote', 'VARCHAR2',       False, False),
    ('whiteList',   'VARCHAR2',       False, False),
    ('fcmToken',    'VARCHAR2',       False, False),
], '#3A7BD5')

# TASK  (우상)
T_x, T_y = 12.0, 9.2
draw_table(ax, T_x, T_y, 'TASK (과업)', [
    ('taskId',     'NUMBER PK',    True,  False),
    ('name',       'VARCHAR2',     False, False),
    ('process',    'CLOB',         False, False),
    ('guardianId', 'VARCHAR2 FK',  False, True),
], '#1A7A3C')

# SCHEDULE  (중앙)
S_x, S_y = 6.0, 8.5
draw_table(ax, S_x, S_y, 'SCHEDULE (일정)', [
    ('scheduleId',   'NUMBER PK',    True,  False),
    ('taskId',       'NUMBER FK',    False, True),
    ('id',           'VARCHAR2 FK',  False, True),
    ('idx',          'NUMBER FK',    False, True),
    ('status',       'VARCHAR2',     False, False),
    ('scheduledAt',  'TIMESTAMP',    False, False),
    ('createdAt',    'TIMESTAMP',    False, False),
    ('successAt',    'TIMESTAMP',    False, False),
    ('location',     'VARCHAR2',     False, False),
    ('specialNote',  'VARCHAR2',     False, False),
    ('customSteps',  'CLOB',         False, False),
    ('questionCount','NUMBER',       False, False),
    ('lastQuestionAt','TIMESTAMP',   False, False),
], '#9B59B6')

# ── 관계선 ──────────────────────────────────────────────────────────────────
# GUARDIAN → NUNGIL_USER (1:N)
ax.annotate('', xy=(U_x+3.2, 3.0), xytext=(G_x+3.2, 5.5),
    arrowprops=dict(arrowstyle='->', color='#1A5F9E', lw=2,
                    connectionstyle='arc3,rad=0'))
ax.text(G_x+3.35, 4.2, '1 : N', fontproperties=FPB, fontsize=9, color='#1A5F9E')

# GUARDIAN → TASK (1:N)
ax.annotate('', xy=(T_x, 7.4), xytext=(G_x+3.2, 7.0),
    arrowprops=dict(arrowstyle='->', color='#1A7A3C', lw=2,
                    connectionstyle='arc3,rad=-0.2'))
ax.text(7.2, 8.0, '1 : N', fontproperties=FPB, fontsize=9, color='#1A7A3C')

# GUARDIAN → SCHEDULE (1:N via id)
ax.annotate('', xy=(S_x, 6.1), xytext=(G_x+3.2, 6.5),
    arrowprops=dict(arrowstyle='->', color='#1A5F9E', lw=1.5, linestyle='dashed',
                    connectionstyle='arc3,rad=0.1'))

# NUNGIL_USER → SCHEDULE (via id+idx)
ax.annotate('', xy=(S_x, 2.8), xytext=(U_x+3.2, 2.5),
    arrowprops=dict(arrowstyle='->', color='#3A7BD5', lw=1.5, linestyle='dashed',
                    connectionstyle='arc3,rad=-0.1'))

# TASK → SCHEDULE (1:N)
ax.annotate('', xy=(S_x+3.2, 5.8), xytext=(T_x, 7.0),
    arrowprops=dict(arrowstyle='->', color='#1A7A3C', lw=2,
                    connectionstyle='arc3,rad=0.2'))
ax.text(10.5, 6.5, '1 : N', fontproperties=FPB, fontsize=9, color='#1A7A3C')

# ── 범례 ────────────────────────────────────────────────────────────────────
legend_items = [
    mpatches.Patch(color='#CC3333', label='PK  기본키'),
    mpatches.Patch(color='#1A5F9E', label='FK  외래키'),
    mpatches.Patch(color='#9B59B6', label='CLOB  대용량 텍스트'),
]
ax.legend(handles=legend_items, loc='lower right', prop=FP, fontsize=9,
          framealpha=0.9, edgecolor='#CCCCCC')

ax.set_title('눈길 DB ERD  (Entity Relationship Diagram)', fontsize=14,
             weight='bold', pad=10, fontproperties=FPB)

plt.tight_layout()
plt.savefig(r'C:\Users\User\Desktop\diagram_erd.png', dpi=150,
            bbox_inches='tight', facecolor=fig.get_facecolor())
plt.close()
print('ERD done')
