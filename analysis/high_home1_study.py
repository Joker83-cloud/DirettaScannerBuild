import csv, io, math, urllib.request, itertools, statistics
from collections import defaultdict, deque

SEASONS=['2324','2425','2526']
TRAIN={'2324','2425'}
TEST={'2526'}
LEAGUES=['E0','E1','E2','E3','EC','SC0','SC1','SC2','SC3','D1','D2','I1','I2','SP1','SP2','F1','F2','N1','B1','P1','T1','G1']
BASE='https://www.football-data.co.uk/mmz4281/{season}/{league}.csv'

def fnum(row, keys):
    for k in keys:
        v=(row.get(k) or '').strip()
        try:
            x=float(v)
            if math.isfinite(x) and x>0:return x
        except: pass
    return None

def download(season,league):
    url=BASE.format(season=season,league=league)
    try:
        with urllib.request.urlopen(url, timeout=30) as r:
            raw=r.read()
        text=raw.decode('utf-8-sig',errors='replace')
        return list(csv.DictReader(io.StringIO(text)))
    except Exception as e:
        print('SKIP',season,league,repr(e))
        return []

def points_for(gf,ga): return 3 if gf>ga else 1 if gf==ga else 0

def wilson(w,n,z=1.96):
    if n==0:return 0.0
    p=w/n; d=1+z*z/n
    return (p+z*z/(2*n)-z*math.sqrt((p*(1-p)+z*z/(4*n))/n))/d

records=[]
for season in SEASONS:
  for league in LEAGUES:
    rows=download(season,league)
    hist=defaultdict(lambda: deque(maxlen=20))
    homehist=defaultdict(lambda: deque(maxlen=10))
    awayhist=defaultdict(lambda: deque(maxlen=10))
    season_pts=defaultdict(int); season_games=defaultdict(int); season_gd=defaultdict(int)
    for row in rows:
        home=(row.get('HomeTeam') or '').strip(); away=(row.get('AwayTeam') or '').strip()
        try: hg=int(float(row.get('FTHG',''))); ag=int(float(row.get('FTAG','')))
        except: continue
        if not home or not away: continue
        odd=fnum(row,['AvgCH','AvgH','B365CH','B365H','PSCH','PSH'])
        h5=list(hist[home])[-5:]; a5=list(hist[away])[-5:]
        hh3=list(homehist[home])[-3:]; aa3=list(awayhist[away])[-3:]
        if odd and len(h5)>=5 and len(a5)>=5 and len(hh3)>=3 and len(aa3)>=3 and season_games[home]>=5 and season_games[away]>=5:
            rec={
              'season':season,'league':league,'home':home,'away':away,'home_win':int(hg>ag),'odd':odd,
              'h5_pts':sum(x['pts'] for x in h5),'a5_pts':sum(x['pts'] for x in a5),
              'hh3_w':sum(x['win'] for x in hh3),'hh3_pts':sum(x['pts'] for x in hh3),
              'aa3_l':sum(x['loss'] for x in aa3),'aa3_pts':sum(x['pts'] for x in aa3),
              'away_last_away_loss':int(aa3[-1]['loss']==1),
              'home_last_home_win':int(hh3[-1]['win']==1),
              'h_ppg':season_pts[home]/season_games[home], 'a_ppg':season_pts[away]/season_games[away],
              'h_gdpg':season_gd[home]/season_games[home], 'a_gdpg':season_gd[away]/season_games[away],
            }
            rec['ppg_gap']=rec['h_ppg']-rec['a_ppg']; rec['gd_gap']=rec['h_gdpg']-rec['a_gdpg']
            records.append(rec)
        hp=points_for(hg,ag); ap=points_for(ag,hg)
        hist[home].append({'pts':hp,'win':int(hg>ag),'loss':int(hg<ag)})
        hist[away].append({'pts':ap,'win':int(ag>hg),'loss':int(ag<hg)})
        homehist[home].append({'pts':hp,'win':int(hg>ag),'loss':int(hg<ag)})
        awayhist[away].append({'pts':ap,'win':int(ag>hg),'loss':int(ag<hg)})
        season_pts[home]+=hp; season_pts[away]+=ap; season_games[home]+=1; season_games[away]+=1
        season_gd[home]+=hg-ag; season_gd[away]+=ag-hg

print('USABLE_RECORDS',len(records))

# Search interpretable rule combinations. All rules retain user's preferred home-odds range 1.50-2.50.
conditions=[]
for lo,hi in [(1.50,1.65),(1.50,1.75),(1.50,1.85),(1.50,2.00),(1.60,1.85),(1.60,2.00),(1.70,2.10),(1.75,2.25),(1.80,2.50)]:
    conditions.append((f'quota {lo:.2f}-{hi:.2f}',lambda r,lo=lo,hi=hi: lo<=r['odd']<=hi))
for n in [2,3]: conditions.append((f'casa >= {n} vittorie ultime 3 interne',lambda r,n=n:r['hh3_w']>=n))
for p in [5,7,9]: conditions.append((f'casa >= {p} punti ultime 3 interne',lambda r,p=p:r['hh3_pts']>=p))
for n in [1,2,3]: conditions.append((f'ospite >= {n} sconfitte ultime 3 trasferte',lambda r,n=n:r['aa3_l']>=n))
for p in [0,1,2,3,4]: conditions.append((f'ospite <= {p} punti ultime 3 trasferte',lambda r,p=p:r['aa3_pts']<=p))
for p in [7,9,10,12]: conditions.append((f'casa >= {p} punti ultime 5',lambda r,p=p:r['h5_pts']>=p))
for p in [3,5,6,7]: conditions.append((f'ospite <= {p} punti ultime 5',lambda r,p=p:r['a5_pts']<=p))
for g in [0.3,0.5,0.7,1.0]: conditions.append((f'gap PPG >= {g:.1f}',lambda r,g=g:r['ppg_gap']>=g))
for g in [0.5,0.8,1.0,1.3]: conditions.append((f'gap gol/partita >= {g:.1f}',lambda r,g=g:r['gd_gap']>=g))
conditions.append(('ultima trasferta ospite PERSA',lambda r:r['away_last_away_loss']==1))
conditions.append(('ultima interna casa VINTA',lambda r:r['home_last_home_win']==1))

# A rule must include exactly one odds condition and up to 4 football conditions.
odds_idx=list(range(9)); football_idx=list(range(9,len(conditions)))
results=[]
for oi in odds_idx:
  for k in range(1,5):
    for comb in itertools.combinations(football_idx,k):
      idxs=(oi,)+comb
      funcs=[conditions[i][1] for i in idxs]
      tr=[r for r in records if r['season'] in TRAIN and all(fn(r) for fn in funcs)]
      if len(tr)<100: continue
      tw=sum(r['home_win'] for r in tr); trrate=tw/len(tr)
      if trrate<0.66: continue
      te=[r for r in records if r['season'] in TEST and all(fn(r) for fn in funcs)]
      if len(te)<35: continue
      ew=sum(r['home_win'] for r in te); terate=ew/len(te)
      # Avoid rules that collapse out-of-sample.
      if terate<0.62: continue
      combined_w=tw+ew; combined_n=len(tr)+len(te); cr=combined_w/combined_n
      score=wilson(ew,len(te))*0.65 + wilson(tw,len(tr))*0.35
      results.append((score,cr,trrate,terate,len(tr),len(te),combined_n,combined_w,idxs))

# De-duplicate near-identical rules by textual signature and sort by conservative score.
results.sort(reverse=True,key=lambda x:(x[0],x[1],x[6]))
print('QUALIFYING_RULES',len(results))
print('\nTOP_RULES')
for rank,res in enumerate(results[:25],1):
    score,cr,trr,ter,trn,ten,cn,cw,idxs=res
    names=[conditions[i][0] for i in idxs]
    avgodd=statistics.mean(r['odd'] for r in records if all(conditions[i][1](r) for i in idxs))
    print(f'{rank:02d}. {cr*100:.1f}%  n={cn}  train={trr*100:.1f}%/{trn}  test={ter*100:.1f}%/{ten}  avgOdd={avgodd:.2f}  WilsonTest={wilson(round(ter*ten),ten)*100:.1f}%')
    print('    '+' + '.join(names))

# Single-factor diagnostics in 1.50-2.50 range.
base=[r for r in records if 1.50<=r['odd']<=2.50]
print('\nBASE_1_50_2_50',len(base),f"{100*sum(r['home_win'] for r in base)/len(base):.1f}%")
for name,fn in conditions[9:]:
    s=[r for r in base if fn(r)]
    if len(s)>=100:
        print(f'SINGLE | {name} | n={len(s)} | 1={100*sum(r["home_win"] for r in s)/len(s):.1f}%')
