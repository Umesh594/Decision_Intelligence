import React, { useMemo, useState } from 'react';
import ReactDOM from 'react-dom/client';
import {
  Activity,
  ArrowUpRight,
  BarChart3,
  BrainCircuit,
  Gauge,
  Loader2,
  Play,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Target,
  TrendingUp
} from 'lucide-react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import './styles.css';

type PathInput = {
  name: string;
  startingSalaryLpa: number;
  annualGrowthMean: number;
  annualGrowthStdDev: number;
  layoffProbability: number;
  promotionProbability: number;
  promotionSalaryBoost: number;
  switchProbability: number;
  switchSalaryBoost: number;
  costOfLivingAdjustment: number;
};

type SimulationRequest = {
  title: string;
  currentSalaryLpa: number;
  offerSalaryLpa: number;
  location: string;
  targetSalaryLpa: number;
  years: number;
  iterations: number;
  riskTolerance: 'LOW' | 'MEDIUM' | 'HIGH';
  calibration: {
    roleLevel: string;
    companyTier: string;
    marketDemand: string;
    cityCostIndex: number;
    interviewConversionRate: number;
    historicalSalaryGrowth: number;
    marketVolatility: number;
    emergencyFundScore: number;
    skillDemandScore: number;
  };
  paths: PathInput[];
};

type PathOutcome = {
  pathName: string;
  goalProbability: number;
  medianFinalSalaryLpa: number;
  p10FinalSalaryLpa: number;
  p90FinalSalaryLpa: number;
  expectedWealthLpa: number;
  downsideRisk: number;
  volatility: number;
  riskAdjustedScore: number;
  targetProgressScore: number;
  resilienceScore: number;
  upsideScore: number;
  confidenceScore: number;
  medianSalaryByYear: number[];
};

type SimulationResponse = {
  scenarioId: string;
  runId: string;
  winner: string;
  aiInsight: string;
  outcomes: PathOutcome[];
};

type MarketIntelligenceResponse = {
  p25SalaryLpa: number;
  medianSalaryLpa: number;
  p75SalaryLpa: number;
  p90SalaryLpa: number;
  marketDemandScore: number;
  confidenceScore: number;
  recommendedCalibration: SimulationRequest['calibration'];
  signals: string[];
  dataSources: string[];
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
const apiKey = import.meta.env.VITE_API_KEY ?? 'local-dev-key';

const initialRequest: SimulationRequest = {
  title: 'Software Intern',
  currentSalaryLpa: 1.8,
  offerSalaryLpa: 6.5,
  location: 'Hyderabad',
  targetSalaryLpa: 12,
  years: 4,
  iterations: 5000,
  riskTolerance: 'LOW',
  calibration: {
    roleLevel: 'Junior',
    companyTier: 'TIER_2',
    marketDemand: 'HIGH',
    cityCostIndex: 1.08,
    interviewConversionRate: 0.18,
    historicalSalaryGrowth: 0.16,
    marketVolatility: 0.16,
    emergencyFundScore: 0.45,
    skillDemandScore: 0.82
  },
  paths: [
    {
      name: 'Stay',
      startingSalaryLpa: 1.8,
      annualGrowthMean: 0.16,
      annualGrowthStdDev: 0.07,
      layoffProbability: 0.04,
      promotionProbability: 0.18,
      promotionSalaryBoost: 0.22,
      switchProbability: 0.22,
      switchSalaryBoost: 0.35,
      costOfLivingAdjustment: 0.04
    },
    {
      name: 'Switch',
      startingSalaryLpa: 6.5,
      annualGrowthMean: 0.12,
      annualGrowthStdDev: 0.09,
      layoffProbability: 0.07,
      promotionProbability: 0.22,
      promotionSalaryBoost: 0.2,
      switchProbability: 0.22,
      switchSalaryBoost: 0.26,
      costOfLivingAdjustment: 0.05
    }
  ]
};

function App() {
  const [request, setRequest] = useState<SimulationRequest>(initialRequest);
  const [response, setResponse] = useState<SimulationResponse | null>(null);
  const [market, setMarket] = useState<MarketIntelligenceResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [calibrating, setCalibrating] = useState(false);
  const [error, setError] = useState('');

  const best = response?.outcomes[0];
  const chartData = useMemo(() => response?.outcomes.map((outcome) => ({
    path: outcome.pathName,
    goal: outcome.goalProbability,
    score: outcome.riskAdjustedScore,
    progress: outcome.targetProgressScore,
    downside: outcome.downsideRisk,
    upside: outcome.upsideScore
  })) ?? [], [response]);

  const timelineData = useMemo(() => {
    if (!response?.outcomes.length) return [];
    return Array.from({ length: request.years }, (_, index) => {
      const row: Record<string, number | string> = { year: `Y${index + 1}` };
      response.outcomes.forEach((outcome) => {
        row[outcome.pathName] = outcome.medianSalaryByYear[index] ?? 0;
      });
      return row;
    });
  }, [request.years, response]);

  async function runSimulation() {
    setLoading(true);
    setError('');
    try {
      const normalized = {
        ...request,
        years: Math.max(1, request.years),
        iterations: Math.max(1000, request.iterations)
      };
      const result = await fetch(`${apiBaseUrl}/api/v1/simulations`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-API-Key': apiKey
        },
        body: JSON.stringify(normalized)
      });
      if (!result.ok) throw new Error(`Simulation failed with status ${result.status}`);
      setRequest(normalized);
      setResponse(await result.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Simulation failed');
    } finally {
      setLoading(false);
    }
  }

  async function calibrateMarket() {
    setCalibrating(true);
    setError('');
    try {
      const result = await fetch(`${apiBaseUrl}/api/v1/market-intelligence/calibrate`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-API-Key': apiKey
        },
        body: JSON.stringify({
          role: request.title,
          location: request.location,
          roleLevel: request.calibration.roleLevel,
          companyTier: request.calibration.companyTier,
          skills: ['Java', 'Spring Boot', 'Microservices', 'Docker', 'Kubernetes', 'AI', 'RAG', 'PostgreSQL', 'Redis']
        })
      });
      if (!result.ok) throw new Error(`Market calibration failed with status ${result.status}`);
      const nextMarket: MarketIntelligenceResponse = await result.json();
      setMarket(nextMarket);
      setRequest({
        ...request,
        calibration: nextMarket.recommendedCalibration,
        offerSalaryLpa: Math.max(request.offerSalaryLpa, Number(nextMarket.medianSalaryLpa.toFixed(2)))
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Market calibration failed');
    } finally {
      setCalibrating(false);
    }
  }

  function updatePath(index: number, field: keyof PathInput, value: string) {
    const paths = request.paths.map((path, pathIndex) => {
      if (pathIndex !== index) return path;
      return { ...path, [field]: field === 'name' ? value : Number(value) };
    });
    setRequest({ ...request, paths });
  }

  return (
    <main className="app-shell">
      <aside className="side-rail">
        <div className="logo-mark"><BrainCircuit size={28} /></div>
        <button className="rail-button active" title="Simulation"><Activity size={20} /></button>
        <button className="rail-button" title="Forecast"><TrendingUp size={20} /></button>
        <button className="rail-button" title="Risk"><ShieldCheck size={20} /></button>
        <button className="rail-button" title="Settings"><SlidersHorizontal size={20} /></button>
      </aside>

      <section className="content">
        <header className="hero">
          <div>
            <div className="eyebrow"><Sparkles size={16} /> AI Decision Consequence Simulator</div>
            <h1>Model career moves like a decision lab.</h1>
            <p>Compare paths with Monte Carlo forecasting, risk-adjusted scoring, resilience, upside and Groq-powered insight.</p>
          </div>
          <button className="run-button" onClick={runSimulation} disabled={loading}>
            {loading ? <Loader2 className="spin" size={18} /> : <Play size={18} />}
            Run Simulation
          </button>
          <button className="calibrate-button" onClick={calibrateMarket} disabled={calibrating}>
            {calibrating ? <Loader2 className="spin" size={18} /> : <Sparkles size={18} />}
            Calibrate Market
          </button>
        </header>

        <section className="kpi-strip">
          <Kpi icon={<Target />} label="Target" value={`${request.targetSalaryLpa} LPA`} />
          <Kpi icon={<Gauge />} label="Iterations" value={request.iterations.toLocaleString()} />
          <Kpi icon={<BarChart3 />} label="Best Path" value={best?.pathName ?? 'Pending'} />
          <Kpi icon={<ShieldCheck />} label="Market Confidence" value={market ? `${market.confidenceScore}%` : best ? `${best.confidenceScore}%` : 'Pending'} />
        </section>

        <div className="layout-grid">
          <section className="sheet scenario-sheet">
            <div className="sheet-head">
              <h2>Scenario Builder</h2>
              <span>{request.location}</span>
            </div>
            <div className="explainer">
              Current and offer salaries are in LPA. Growth, layoff, promotion and switch are yearly probabilities or rates. More years gives the simulator more time to reach the target.
            </div>
            <div className="form-grid">
              <TextInput label="Title" value={request.title} onChange={(value) => setRequest({ ...request, title: value })} />
              <TextInput label="Location" value={request.location} onChange={(value) => setRequest({ ...request, location: value })} />
              <NumberInput label="Current LPA" value={request.currentSalaryLpa} onChange={(value) => setRequest({ ...request, currentSalaryLpa: value })} />
              <NumberInput label="Offer LPA" value={request.offerSalaryLpa} onChange={(value) => setRequest({ ...request, offerSalaryLpa: value })} />
              <NumberInput label="Target LPA" value={request.targetSalaryLpa} onChange={(value) => setRequest({ ...request, targetSalaryLpa: value })} />
              <NumberInput label="Years" min={1} max={15} value={request.years} onChange={(value) => setRequest({ ...request, years: Math.max(1, value) })} />
              <NumberInput label="Iterations" min={1000} max={100000} value={request.iterations} onChange={(value) => setRequest({ ...request, iterations: Math.max(1000, value) })} />
              <label className="field">Risk
                <select value={request.riskTolerance} onChange={(event) => setRequest({ ...request, riskTolerance: event.target.value as SimulationRequest['riskTolerance'] })}>
                  <option>LOW</option>
                  <option>MEDIUM</option>
                  <option>HIGH</option>
                </select>
              </label>
            </div>

            <div className="path-stack">
              {market && (
                <article className="market-card">
                  <div className="path-title">
                    <h3>Live Market Intelligence</h3>
                    <span>{market.confidenceScore}% confidence</span>
                  </div>
                  <div className="salary-band">
                    <Metric label="P25" value={`${market.p25SalaryLpa} LPA`} />
                    <Metric label="Median" value={`${market.medianSalaryLpa} LPA`} />
                    <Metric label="P75" value={`${market.p75SalaryLpa} LPA`} />
                    <Metric label="P90" value={`${market.p90SalaryLpa} LPA`} />
                  </div>
                  <div className="signal-list">
                    {market.signals.map((signal) => <span key={signal}>{signal}</span>)}
                  </div>
                </article>
              )}

              <article className="path-card calibration-card">
                <div className="path-title">
                  <h3>Market Calibration</h3>
                  <span>Business inputs</span>
                </div>
                <div className="mini-grid">
                  <label className="field">Role Level
                    <select value={request.calibration.roleLevel} onChange={(event) => setRequest({ ...request, calibration: { ...request.calibration, roleLevel: event.target.value } })}>
                      <option>Intern</option>
                      <option>Fresher</option>
                      <option>Junior</option>
                      <option>Mid</option>
                      <option>Senior</option>
                    </select>
                  </label>
                  <label className="field">Company Tier
                    <select value={request.calibration.companyTier} onChange={(event) => setRequest({ ...request, calibration: { ...request.calibration, companyTier: event.target.value } })}>
                      <option>TIER_1</option>
                      <option>TIER_2</option>
                      <option>TIER_3</option>
                      <option>STARTUP</option>
                    </select>
                  </label>
                  <label className="field">Market Demand
                    <select value={request.calibration.marketDemand} onChange={(event) => setRequest({ ...request, calibration: { ...request.calibration, marketDemand: event.target.value } })}>
                      <option>LOW</option>
                      <option>MEDIUM</option>
                      <option>HIGH</option>
                      <option>VERY_HIGH</option>
                    </select>
                  </label>
                  <NumberInput label="City Cost Index" step={0.01} value={request.calibration.cityCostIndex} onChange={(value) => setRequest({ ...request, calibration: { ...request.calibration, cityCostIndex: value } })} />
                  <NumberInput label="Interview Conv." step={0.01} value={request.calibration.interviewConversionRate} onChange={(value) => setRequest({ ...request, calibration: { ...request.calibration, interviewConversionRate: value } })} />
                  <NumberInput label="Historical Growth" step={0.01} value={request.calibration.historicalSalaryGrowth} onChange={(value) => setRequest({ ...request, calibration: { ...request.calibration, historicalSalaryGrowth: value } })} />
                  <NumberInput label="Market Volatility" step={0.01} value={request.calibration.marketVolatility} onChange={(value) => setRequest({ ...request, calibration: { ...request.calibration, marketVolatility: value } })} />
                  <NumberInput label="Emergency Fund" step={0.01} value={request.calibration.emergencyFundScore} onChange={(value) => setRequest({ ...request, calibration: { ...request.calibration, emergencyFundScore: value } })} />
                  <NumberInput label="Skill Demand" step={0.01} value={request.calibration.skillDemandScore} onChange={(value) => setRequest({ ...request, calibration: { ...request.calibration, skillDemandScore: value } })} />
                </div>
              </article>

              {request.paths.map((path, index) => (
                <article className="path-card" key={path.name}>
                  <div className="path-title">
                    <h3>{path.name}</h3>
                    <span>{path.startingSalaryLpa} LPA start</span>
                  </div>
                  <div className="mini-grid">
                    <NumberInput label="Start" value={path.startingSalaryLpa} onChange={(value) => updatePath(index, 'startingSalaryLpa', String(value))} />
                    <NumberInput label="Growth" step={0.01} value={path.annualGrowthMean} onChange={(value) => updatePath(index, 'annualGrowthMean', String(value))} />
                    <NumberInput label="Volatility" step={0.01} value={path.annualGrowthStdDev} onChange={(value) => updatePath(index, 'annualGrowthStdDev', String(value))} />
                    <NumberInput label="Layoff" step={0.01} value={path.layoffProbability} onChange={(value) => updatePath(index, 'layoffProbability', String(value))} />
                    <NumberInput label="Promotion" step={0.01} value={path.promotionProbability} onChange={(value) => updatePath(index, 'promotionProbability', String(value))} />
                    <NumberInput label="Switch" step={0.01} value={path.switchProbability} onChange={(value) => updatePath(index, 'switchProbability', String(value))} />
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className="results-stack">
            <div className="sheet outcome-sheet">
              <div className="sheet-head">
                <h2>Decision Intelligence</h2>
                <span>{response ? `Run ${response.runId.slice(0, 8)}` : 'Awaiting run'}</span>
              </div>
              {error && <div className="error">{error}</div>}
              {response ? (
                <>
                  <div className="winner-card">
                    <div>
                      <p>Best modeled path</p>
                      <h3>{response.winner}</h3>
                    </div>
                    <ArrowUpRight size={26} />
                  </div>
                  <ResponsiveContainer width="100%" height={260}>
                    <BarChart data={chartData}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#dfe7e3" />
                      <XAxis dataKey="path" />
                      <YAxis />
                      <Tooltip />
                      <Legend />
                      <Bar dataKey="score" name="Risk Score" fill="#216869" radius={[6, 6, 0, 0]} />
                      <Bar dataKey="progress" name="Target Progress %" fill="#284b63" radius={[6, 6, 0, 0]} />
                      <Bar dataKey="goal" name="Goal %" fill="#49a078" radius={[6, 6, 0, 0]} />
                      <Bar dataKey="downside" name="Downside %" fill="#c8553d" radius={[6, 6, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </>
              ) : (
                <div className="empty-state">Run a scenario to see ranked paths, risk, upside and confidence.</div>
              )}
            </div>

            {response && (
              <>
                <div className="sheet">
                  <div className="sheet-head">
                    <h2>Salary Timeline</h2>
                    <span>Median LPA</span>
                  </div>
                  <ResponsiveContainer width="100%" height={230}>
                    <AreaChart data={timelineData}>
                      <defs>
                        <linearGradient id="stay" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="#284b63" stopOpacity={0.7} />
                          <stop offset="100%" stopColor="#284b63" stopOpacity={0.08} />
                        </linearGradient>
                        <linearGradient id="switch" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="#49a078" stopOpacity={0.75} />
                          <stop offset="100%" stopColor="#49a078" stopOpacity={0.08} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#dfe7e3" />
                      <XAxis dataKey="year" />
                      <YAxis />
                      <Tooltip />
                      <Legend />
                      {response.outcomes.map((outcome, index) => (
                        <Area
                          key={outcome.pathName}
                          type="monotone"
                          dataKey={outcome.pathName}
                          stroke={index === 0 ? '#49a078' : '#284b63'}
                          fill={index === 0 ? 'url(#switch)' : 'url(#stay)'}
                          strokeWidth={3}
                        />
                      ))}
                    </AreaChart>
                  </ResponsiveContainer>
                </div>

                <div className="comparison-grid">
                  {response.outcomes.map((outcome) => (
                    <article className="metric-card" key={outcome.pathName}>
                      <div className="metric-top">
                        <h3>{outcome.pathName}</h3>
                        <span>{outcome.riskAdjustedScore}</span>
                      </div>
                      <Metric label="Goal Probability" value={`${outcome.goalProbability}%`} />
                      <Metric label="Target Progress" value={`${outcome.targetProgressScore}%`} />
                      <Metric label="Median Final" value={`${outcome.medianFinalSalaryLpa} LPA`} />
                      <Metric label="Expected Wealth" value={`${outcome.expectedWealthLpa} LPA`} />
                      <Metric label="Downside Risk" value={`${outcome.downsideRisk}%`} />
                      <Metric label="Resilience" value={`${outcome.resilienceScore}%`} />
                      <Metric label="Upside" value={`${outcome.upsideScore}%`} />
                    </article>
                  ))}
                </div>

                <div className="sheet insight-sheet">
                  <div className="sheet-head">
                    <h2>Groq Insight</h2>
                    <span>{response.aiInsight.startsWith('Simulation-only') ? 'Fallback' : 'Groq'}</span>
                  </div>
                  <p>{response.aiInsight}</p>
                </div>

                <div className="sheet glossary-sheet">
                  <div className="sheet-head">
                    <h2>How To Read This</h2>
                    <span>Metric guide</span>
                  </div>
                  <div className="glossary-grid">
                    <Metric label="Goal Probability" value="Chance of reaching target LPA" />
                    <Metric label="Risk Score" value="Balanced score after risk" />
                    <Metric label="Target Progress" value="Median salary / target" />
                    <Metric label="Downside Risk" value="Layoff shock frequency" />
                    <Metric label="Resilience" value="Recovery after shock" />
                    <Metric label="Expected Wealth" value="Total modeled salary" />
                    <Metric label="City Cost Index" value="1.0 baseline, higher means expensive city" />
                    <Metric label="Interview Conv." value="Chance pipeline produces offers" />
                    <Metric label="Skill Demand" value="How hot your skill is in market" />
                  </div>
                </div>
              </>
            )}
          </section>
        </div>
      </section>
    </main>
  );
}

function Kpi({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return <article className="kpi-card"><div>{icon}</div><span>{label}</span><strong>{value}</strong></article>;
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="metric-row"><span>{label}</span><strong>{value}</strong></div>;
}

function TextInput({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <label className="field">{label}<input value={value} onChange={(event) => onChange(event.target.value)} /></label>;
}

function NumberInput({ label, value, min, max, step = 0.01, onChange }: {
  label: string;
  value: number;
  min?: number;
  max?: number;
  step?: number;
  onChange: (value: number) => void;
}) {
  return (
    <label className="field">
      {label}
      <input type="number" min={min} max={max} step={step} value={value} onChange={(event) => onChange(Number(event.target.value))} />
    </label>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(<App />);
