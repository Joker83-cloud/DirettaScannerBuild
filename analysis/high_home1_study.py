import csv, io, math, urllib.request, itertools, statistics
from collections import defaultdict, deque

SEASONS=['2324','2425','2526']
TRAIN={'2324','2425'}
TEST={'2526'}
LEAGUES=['E0','E1','E2','E3','EC','SC0','SC1','SC2','SC3','D1','D2','I1','I2','SP1','SP2','F1','F2','N1','B1','P1','T1','G1']
BASE='https://www.football-data.co.uk/mmz4281/{season}/{league}.csv'

def fnum(row, keys):
    for k in keys:
        try:
            x=float((row.get(k) or '').strip())
            if math.isfinite(x) and x>0:return x
        except: pass
    return None

def download(season,league):
    try:
        with urllib.request.urlopen(BASE.format(season=season,league=league), timeout=30) as r: raw=r.read()
        return list(csv.DictReader(io.StringIO(raw.decode('utf-8-sig',errors='replace'))))
    except Exception as e:
        print('SKIP',season,league,repr(e)); return []

def pts(gf,ga): return 3 if gf>ga else 1 if gf==ga else 0

def wilson(w,n,z=1.96):
    if not n:return 0.0
    p=w/n; d=1+z*z/n
    return (p+z*z/(2*n)-z*math.sqrt((p*(1-p)+z*z/(4*n))/n))/d

records=[]
for season in SEASONS:
  for league in LEAGUES:
    hist=defaultdict(lambda: deque(maxlen=20)); homehist=defaultdict(lambda: deque(maxlen=10)); awayhist=defaultdict(lambda: deque(maxlen=10))
    sp=defaultdict(int); sg=defaultdict(int); sgd=defaultdict(int)
    for row in download(season,league):
        home=(row.get('HomeTeam') or '').strip(); away=(row.get('AwayTeam') or '').strip()
        try: hg=int(float(row.get('FTHG',''))); ag=int(float(row.get('FTAG','')))
        except: continue
        if not home or not away: continue
        odd=fnum(row,['AvgCH','AvgH','B365CH','B365H','PSCH','PSH'])
        h5=list(hist[home])[-5:]; a5=list(hist[away])[-5:]; hh3=list(homehist[home])[-3:]; aa3=list(awayhist[away])[-3:]
        if odd and len(h5)>=5 and len(a5)>=5 and len(hh3)>=3 and len(aa3)>=3 and sg[home]>=5 and sg[away]>=5:
            r={'season':season,'league':league,'home':home,'away':away,'home_win':int(hg>ag),'odd':odd,
               'h5_pts':sum(x['pts'] for x in h5),'a5_pts':sum(x['pts'] for x in a5),
               'h5_w':sum(x['win'] for x in h5),'a5_l':sum(x['loss'] for x in a5),
               'h5_gf':sum(x['gf'] for x in h5)/5,'h5_ga':sum(x['ga'] for x in h5)/5,
               'a5_gf':sum(x['gf'] for x in a5)/5,'a5_ga':sum(x['ga'] for x in a5)/5,
               'hh3_w':sum(x['win'] for x in hh3),'hh3_pts':sum(x['pts'] for x in hh3),
               'hh3_gf':sum(x['gf'] for x in hh3)/3,'hh3_ga':sum(x['ga'] for x in hh3)/3,
               'aa3_l':sum(x['loss'] for x in aa3),'aa3_pts':sum(x['pts'] for x in aa3),
               'aa3_gf':sum(x['gf'] for x in aa3)/3,'aa3_ga':sum(x['ga'] for x in aa3)/3,
               'away_last_away_loss':aa3[-1]['loss'],'home_last_home_win':hh3[-1]['win'],
               'ppg_gap':sp[home]/sg[home]-sp[away]/sg[away],
               'gd_gap':sgd[home]/sg[home]-sgd[away]/sg[away]}
            records.append(r)
        hp=pts(hg,ag); ap=pts(ag,hg)
        hitem={'pts':hp,'win':int(hg>ag),'loss':int(hg<ag),'gf':hg,'ga':ag}; aitem={'pts':ap,'win':int(ag>hg),'loss':int(ag<hg),'gf':ag,'ga':hg}
        hist[home].append(hitem); hist[away].append(aitem); homehist[home].append(hitem); awayhist[away].append(aitem)
        sp[home]+=hp; sp[away]+=ap; sg[home]+=1; sg[away]+=1; sgd[home]+=hg-ag; sgd[away]+=ag-hg

print('USABLE_RECORDS',len(records))
conditions=[]
for lo,hi in [(1.50,1.58),(1.50,1.60),(1.50,1.65),(1.50,1.70),(1.55,1.70),(1.60,1.75),(1.60,1.85),(1.65,1.85),(1.70,2.00)]:
    conditions.append((f'quota {lo:.2f}-{hi:.2f}',lambda r,lo=lo,hi=hi:lo<=r['odd']<=hi))
for n in [2,3]: conditions.append((f'casa >= {n} vittorie ultime 3 interne',lambda r,n=n:r['hh3_w']>=n))
for n in [3,4]: conditions.append((f'casa >= {n} vittorie ultime 5',lambda r,n=n:r['h5_w']>=n))
for n in [2,3]: conditions.append((f'ospite >= {n} sconfitte ultime 3 trasferte',lambda r,n=n:r['aa3_l']>=n))
for n in [3,4]: conditions.append((f'ospite >= {n} sconfitte ultime 5',lambda r,n=n:r['a5_l']>=n))
for p in [9,10,12]: conditions.append((f'casa >= {p} punti ultime 5',lambda r,p=p:r['h5_pts']>=p))
for p in [3,5,6]: conditions.append((f'ospite <= {p} punti ultime 5',lambda r,p=p:r['a5_pts']<=p))
for g in [1.5,1.8,2.0]: conditions.append((f'casa GF ultime 3 interne >= {g:.1f}',lambda r,g=g:r['hh3_gf']>=g))
for g in [0.7,1.0]: conditions.append((f'casa GA ultime 3 interne <= {g:.1f}',lambda r,g=g:r['hh3_ga']<=g))
for g in [1.3,1.7,2.0]: conditions.append((f'ospite GA ultime 3 trasferte >= {g:.1f}',lambda r,g=g:r['aa3_ga']>=g))
for g in [0.7,1.0]: conditions.append((f'ospite GF ultime 3 trasferte <= {g:.1f}',lambda r,g=g:r['aa3_gf']<=g))
for g in [0.5,0.7,1.0]: conditions.append((f'gap PPG >= {g:.1f}',lambda r,g=g:r['ppg_gap']>=g))
for g in [0.8,1.0,1.3]: conditions.append((f'gap gol/partita >= {g:.1f}',lambda r,g=g:r['gd_gap']>=g))
conditions += [('ultima trasferta ospite PERSA',lambda r:r['away_last_away_loss']==1),('ultima interna casa VINTA',lambda r:r['home_last_home_win']==1)]

train_all={i for i,r in enumerate(records) if r['season'] in TRAIN}; test_all={i for i,r in enumerate(records) if r['season'] in TEST}; wins={i for i,r in enumerate(records) if r['home_win']}
mask=[{i for i,r in enumerate(records) if fn(r)} for _,fn in conditions]
def inter(idxs,base):
    s=base & mask[idxs[0]]
    for j in idxs[1:]: s &= mask[j]
    return s

results=[]; football_idx=range(9,len(conditions))
for oi in range(9):
  for k in range(1,5):
    for comb in itertools.combinations(football_idx,k):
      idxs=(oi,)+comb; tr=inter(idxs,train_all); trn=len(tr)
      if trn<80: continue
      tw=len(tr&wins); trr=tw/trn
      if trr<0.68: continue
      te=inter(idxs,test_all); ten=len(te)
      if ten<30: continue
      ew=len(te&wins); ter=ew/ten
      if ter<0.66: continue
      cn=trn+ten; cw=tw+ew; cr=cw/cn
      score=0.7*wilson(ew,ten)+0.3*wilson(tw,trn)
      results.append((score,cr,trr,ter,trn,ten,cn,cw,idxs))

results.sort(reverse=True,key=lambda x:(x[0],x[1],x[6]))
print('QUALIFYING_RULES',len(results)); print('\nTOP_RULES')
seen=set(); rank=0
for res in results:
    score,cr,trr,ter,trn,ten,cn,cw,idxs=res
    names=[conditions[i][0] for i in idxs]
    # suppress logically redundant threshold stacks
    norm=tuple(names)
    if norm in seen: continue
    seen.add(norm); rank+=1
    allset=inter(idxs,set(range(len(records)))); avgodd=statistics.mean(records[i]['odd'] for i in allset)
    print(f'{rank:02d}. {cr*100:.1f}%  n={cn}  train={trr*100:.1f}%/{trn}  test={ter*100:.1f}%/{ten}  avgOdd={avgodd:.2f}  WilsonTest={wilson(ew,ten)*100:.1f}%')
    print('    '+' + '.join(names))
    if rank>=30: break

print('\nODDS_BASELINES')
for oi in range(9):
    s=mask[oi]; print(f'{conditions[oi][0]} | n={len(s)} | 1={100*len(s&wins)/len(s):.1f}%')
print('\nKEY_SINGLE_FACTORS_IN_1.50_2.50')
base={i for i,r in enumerate(records) if 1.50<=r['odd']<=2.50}
for j,(name,fn) in enumerate(conditions[9:],9):
    s=base & mask[j]
    if len(s)>=100: print(f'{name} | n={len(s)} | 1={100*len(s&wins)/len(s):.1f}%')
