// Datos de demostración de la consola administrativa. Se reemplazarán por
// llamadas al backend cuando existan los endpoints.

export type Solicitud = {
  id: string;
  num: string;
  nombre: string;
  dui: string;
  tel: string;
  /** Fecha de la solicitud, dd/mm/aaaa. */
  f: string;
  /** Hora de inicio, hh:mm:ss. */
  ini: string;
  /** Factor de velocidad de la persona al llenar cada paso. */
  k: number;
  /** Etapas completadas, de 0 a 5. */
  c: number;
  origen: string | null;
  nivel: string | null;
  tipo: string | null;
  monto: number | null;
  ip: string;
  ubic: string;
  huella: string;
  disp: string;
  ritmo: string;
};

export type Criticidad = "Crítica" | "Alta" | "Media" | "Baja";
export type EstadoAlerta = "Sin asignar" | "Asignada" | "En revisión";

export type Alerta = {
  id: string;
  crit: Criticidad;
  cuenta: string;
  motivo: string;
  estado: EstadoAlerta;
  resp: string;
  /** Día del mes en que se generó (para los filtros de fecha). */
  d: number;
  ts: string;
  fecha: string;
};

export type Rol = "gerardo" | "ana";
export type Usuario = { nombre: string; cargo: string; ini: string };

/** Estado de carga de los datos, para mostrar los estados vacío, error y carga. */
export type EstadoDatos = "normal" | "cargando" | "error" | "vacio";

export const SOLICITUDES: Solicitud[] = [
  { id: "s1", num: "SOL-2026-00418", nombre: "Marta Alejandra Rivas Cruz", dui: "04812377-5", tel: "7845-2310", f: "05/10/2026", ini: "22:08:03", k: 1, c: 5, origen: "Salario", nivel: "USD 500 a 1,500", tipo: "Pago de salario", monto: 320, ip: "190.5.142.77", ubic: "San Salvador, El Salvador", huella: "d4f1·9a3c·e7b2", disp: "iPhone 15 · iOS", ritmo: "Normal, unos 185 caracteres por minuto" },
  { id: "s2", num: "SOL-2026-00417", nombre: "Roberto Antonio Menjívar", dui: "03377841-2", tel: "7021-5543", f: "05/10/2026", ini: "16:35:12", k: 1.2, c: 5, origen: "Negocio propio", nivel: "USD 1,500 a 5,000", tipo: "Cobros de su negocio", monto: 2400, ip: "190.5.88.140", ubic: "Santa Ana, El Salvador", huella: "b81e·42d0·5fa9", disp: "Samsung Galaxy A54 · Android", ritmo: "Normal, unos 160 caracteres por minuto" },
  { id: "s3", num: "SOL-2026-00415", nombre: "Karla Vanessa Hernández", dui: "05190462-8", tel: "7312-0087", f: "05/10/2026", ini: "11:00:20", k: 0.9, c: 5, origen: "Remesas", nivel: "USD 500 a 1,500", tipo: "Remesas familiares", monto: 450, ip: "190.86.31.9", ubic: "San Miguel, El Salvador", huella: "a27c·e1b6·90d4", disp: "iPhone 13 · iOS", ritmo: "Normal, unos 150 caracteres por minuto" },
  { id: "s4", num: "", nombre: "Josué Mauricio Flores", dui: "02266518-0", tel: "6190-3374", f: "05/10/2026", ini: "09:28:40", k: 1.1, c: 3, origen: "Negocio propio", nivel: "USD 500 a 1,500", tipo: null, monto: null, ip: "190.5.201.33", ubic: "Soyapango, El Salvador", huella: "3cc9·f0a1·7e55", disp: "Xiaomi Redmi Note 12 · Android", ritmo: "Normal, unos 140 caracteres por minuto" },
  { id: "s5", num: "SOL-2026-00412", nombre: "Daniela Beatriz Ortiz", dui: "04905513-3", tel: "7788-4120", f: "04/10/2026", ini: "20:42:30", k: 0.8, c: 5, origen: "Salario", nivel: "Menos de USD 500", tipo: "Ahorro", monto: 180, ip: "190.86.120.64", ubic: "Santa Tecla, El Salvador", huella: "7d02·b5e8·1c39", disp: "iPhone 14 · iOS", ritmo: "Normal, unos 170 caracteres por minuto" },
  { id: "s6", num: "SOL-2026-00410", nombre: "Carlos Eduardo Aguilar", dui: "01833920-6", tel: "7655-9012", f: "04/10/2026", ini: "15:03:50", k: 1.3, c: 5, origen: "Remesas", nivel: "USD 1,500 a 5,000", tipo: "Remesas familiares", monto: 1100, ip: "190.5.14.200", ubic: "La Libertad, El Salvador", huella: "ee49·0b7a·c613", disp: "Samsung Galaxy S23 · Android", ritmo: "Más rápido que el promedio, unos 310 caracteres por minuto" },
  { id: "s7", num: "SOL-2026-00409", nombre: "Ana Lucía Portillo", dui: "04177305-9", tel: "7210-6678", f: "03/10/2026", ini: "18:19:40", k: 1, c: 5, origen: "Salario", nivel: "USD 500 a 1,500", tipo: "Pago de salario", monto: 750, ip: "190.86.77.18", ubic: "San Salvador, El Salvador", huella: "58a3·d94f·2e01", disp: "iPhone 15 Pro · iOS", ritmo: "Normal, unos 190 caracteres por minuto" },
  { id: "s8", num: "SOL-2026-00406", nombre: "Mauricio Ernesto Villalta", dui: "02984416-1", tel: "7499-3321", f: "03/10/2026", ini: "11:57:10", k: 1.4, c: 5, origen: "Pensión", nivel: "Menos de USD 500", tipo: "Ahorro", monto: 90, ip: "190.5.65.31", ubic: "Sonsonate, El Salvador", huella: "c10b·77e2·a8d5", disp: "Motorola Moto G · Android", ritmo: "Pausado, unos 95 caracteres por minuto" },
  { id: "s9", num: "", nombre: "Sofía Guadalupe Martínez", dui: "05522087-4", tel: "6078-2245", f: "03/10/2026", ini: "09:12:05", k: 1, c: 2, origen: null, nivel: null, tipo: null, monto: null, ip: "190.86.9.250", ubic: "Apopa, El Salvador", huella: "91fd·3a60·be27", disp: "iPhone 12 · iOS", ritmo: "Normal, unos 165 caracteres por minuto" },
  { id: "s10", num: "SOL-2026-00401", nombre: "José Armando Quintanilla", dui: "03640258-7", tel: "7933-1450", f: "02/10/2026", ini: "08:36:00", k: 1.1, c: 5, origen: "Negocio propio", nivel: "Más de USD 5,000", tipo: "Cobros de su negocio", monto: 4800, ip: "190.5.130.12", ubic: "Chalatenango, El Salvador", huella: "2b6e·f813·4d90", disp: "Samsung Galaxy A34 · Android", ritmo: "Normal, unos 175 caracteres por minuto" },
];

export const ALERTAS: Alerta[] = [
  { id: "a1", crit: "Crítica", cuenta: "•••• 4821", motivo: "Movimientos 4 veces por encima del perfil declarado", estado: "Sin asignar", resp: "", d: 5, ts: "2026-10-05 08:12", fecha: "05/10 08:12" },
  { id: "a2", crit: "Crítica", cuenta: "•••• 7730", motivo: "Ingresos recibidos de origen distinto al declarado", estado: "Asignada", resp: "Ana Beltrán", d: 5, ts: "2026-10-05 07:40", fecha: "05/10 07:40" },
  { id: "a3", crit: "Alta", cuenta: "•••• 1156", motivo: "Actividad inusual en horario nocturno", estado: "Sin asignar", resp: "", d: 5, ts: "2026-10-05 02:18", fecha: "05/10 02:18" },
  { id: "a4", crit: "Alta", cuenta: "•••• 9042", motivo: "Dispositivo nuevo con ubicación distinta a la declarada", estado: "En revisión", resp: "Luis Barahona", d: 4, ts: "2026-10-04 17:55", fecha: "04/10 17:55" },
  { id: "a5", crit: "Alta", cuenta: "•••• 3318", motivo: "Varias solicitudes desde el mismo dispositivo", estado: "Asignada", resp: "Ana Beltrán", d: 4, ts: "2026-10-04 15:21", fecha: "04/10 15:21" },
  { id: "a6", crit: "Media", cuenta: "•••• 6205", motivo: "Ritmo de escritura atípico durante el formulario", estado: "Sin asignar", resp: "", d: 4, ts: "2026-10-04 10:03", fecha: "04/10 10:03" },
  { id: "a7", crit: "Media", cuenta: "•••• 5871", motivo: "Monto declarado muy cercano al umbral de riesgo", estado: "Asignada", resp: "Karen Molina", d: 3, ts: "2026-10-03 16:48", fecha: "03/10 16:48" },
  { id: "a8", crit: "Baja", cuenta: "•••• 2094", motivo: "Cambio de teléfono de contacto durante la solicitud", estado: "Sin asignar", resp: "", d: 3, ts: "2026-10-03 19:30", fecha: "03/10 19:30" },
  { id: "a9", crit: "Baja", cuenta: "•••• 8467", motivo: "Intento de acceso fuera del horario habitual", estado: "Asignada", resp: "Karen Molina", d: 3, ts: "2026-10-03 13:12", fecha: "03/10 13:12" },
];

export const USUARIOS: Record<Rol, Usuario> = {
  gerardo: { nombre: "Gerardo Rosales", cargo: "Líder de Conocimiento del Cliente", ini: "GR" },
  ana: { nombre: "Ana Beltrán", cargo: "Analista de fraude y cumplimiento", ini: "AB" },
};

export const SEVERIDAD: Record<Criticidad, number> = { Crítica: 0, Alta: 1, Media: 2, Baja: 3 };
export const CRITICIDAD: Record<Criticidad, [string, string]> = {
  Crítica: ["c-crit", "◆ Crítica"],
  Alta: ["c-alta", "▲ Alta"],
  Media: ["c-media", "■ Media"],
  Baja: ["c-baja", "● Baja"],
};
export const ESTADO_ALERTA: Record<EstadoAlerta, [string, string]> = {
  "Sin asignar": ["s-sin", "○ Sin asignar"],
  Asignada: ["s-asig", "◐ Asignada"],
  "En revisión": ["s-rev", "◉ En revisión"],
};

export const UMBRAL_RIESGO_BAJO = 500;

// ---- Formato

export const usd = (n: number) => "USD " + n.toLocaleString("en-US");
const p2 = (n: number) => (n < 10 ? "0" : "") + n;
export const hms = (s: number) =>
  p2(Math.floor(s / 3600) % 24) + ":" + p2(Math.floor(s / 60) % 60) + ":" + p2(s % 60);
export const hm = (s: number) => hms(s).slice(0, 5);
const segundos = (t: string) => {
  const [h, m, s = 0] = t.split(":").map(Number);
  return h * 3600 + m * 60 + s;
};
const minSeg = (s: number) => Math.floor(s / 60) + ":" + p2(s % 60);

// ---- Reglas de negocio

export type Riesgo = { key: "bajo" | "pend" | "sin"; cls: string; label: string };

/** Regla R-01: monto mensual menor al umbral → riesgo bajo. */
export function riesgoDe(r: Solicitud): Riesgo {
  if (r.monto == null) return { key: "sin", cls: "b-sin", label: "○ Sin evaluar" };
  if (r.monto < UMBRAL_RIESGO_BAJO) return { key: "bajo", cls: "b-bajo", label: "● Bajo" };
  return { key: "pend", cls: "b-pend", label: "◌ Pendiente de evaluación" };
}

export const completada = (r: Solicitud) => r.c >= 5;

/** Duración (s) de cada uno de los 5 pasos y su acumulado. */
function etapas(r: Solicitud) {
  const dur = [68, 72, 48, 63, 41].map((x) => Math.round(x * r.k));
  let t = 0;
  const cum = dur.map((x) => (t += x));
  return { dur, cum };
}

const inicio = (r: Solicitud) => segundos(r.ini);
const fin = (r: Solicitud) => (completada(r) ? inicio(r) + etapas(r).cum[4] : inicio(r));

export const numeroDe = (r: Solicitud) => (completada(r) ? r.num : "Sin número aún");
export const fechaCorta = (r: Solicitud) => r.f.slice(0, 5) + " " + hm(fin(r));

export function estadoSolicitud(r: Solicitud, detalle = false) {
  if (completada(r)) return { cls: "s-ok", label: "✓ Completada" };
  return { cls: "s-prog", label: detalle ? `… En progreso (${r.c} de 5 etapas)` : "… En progreso" };
}

export const buscarSolicitud = (id: string) => SOLICITUDES.find((x) => x.id === id);

export type Par = { k: string; v: string };

/** Todo lo que muestra la pantalla de detalle de una solicitud. */
export function detalleDe(r: Solicitud) {
  const rk = riesgoDe(r);
  const E = etapas(r);
  const t0 = inicio(r);
  const done = completada(r);

  let explicacion: string;
  if (rk.key === "bajo")
    explicacion = `Monto mensual declarado de ${usd(r.monto!)}, menor al umbral de ${usd(UMBRAL_RIESGO_BAJO)}, riesgo bajo.`;
  else if (rk.key === "pend")
    explicacion = `Monto mensual declarado de ${usd(r.monto!)}, igual o mayor al umbral de ${usd(UMBRAL_RIESGO_BAJO)}. La regla vigente solo asigna riesgo bajo, así que este caso queda pendiente de evaluación.`;
  else explicacion = "La solicitud sigue en progreso: todavía no hay un monto declarado que evaluar.";

  const nocturno = done && (Math.floor((t0 + E.cum[4]) / 3600) >= 21 || Math.floor(t0 / 3600) < 5);
  const nombresPasos = ["Aviso de privacidad", "Datos básicos", "Ingresos", "Movimiento esperado", "Revisión"];
  const pasos = nombresPasos.map((t, i) => ({ t, d: r.c > i ? minSeg(E.dur[i]) : "Sin completar" }));

  const en = (s: number) => r.f + " " + hms(s);
  const timeline = [{ t: en(t0), e: "Solicitud iniciada desde la app móvil" }];
  const hitos = [
    "Aviso de privacidad aceptado",
    "Datos básicos completados",
    "Ingresos declarados registrados",
    "Movimiento esperado registrado",
    "Solicitud enviada",
  ];
  hitos.slice(0, r.c).forEach((e, i) => timeline.push({ t: en(t0 + E.cum[i]), e }));
  if (done && rk.key === "bajo")
    timeline.push({ t: en(t0 + E.cum[4] + 1), e: "Score de riesgo asignado: bajo (regla R-01)" });
  if (!done)
    timeline.push({
      t: en(t0 + (r.c >= 1 ? E.cum[r.c - 1] : 0)),
      e: "Última actividad: la persona dejó la solicitud sin terminar",
    });

  const basicos: Par[] = [
    { k: "Nombre completo", v: r.nombre },
    { k: "DUI", v: r.dui },
    { k: "Teléfono celular", v: r.tel },
    { k: "Fecha de solicitud", v: r.f },
  ];
  const ingresos: Par[] | null =
    r.c >= 3
      ? [
          { k: "Origen", v: r.origen || "—" },
          { k: "Nivel mensual", v: r.nivel || "—" },
          { k: "Registrado", v: en(t0 + E.cum[2]) },
        ]
      : null;
  const movimiento: Par[] | null =
    r.c >= 4
      ? [
          { k: "Tipo de dinero", v: r.tipo || "—" },
          { k: "Monto mensual", v: r.monto == null ? "—" : usd(r.monto) },
          { k: "Registrado", v: en(t0 + E.cum[3]) },
        ]
      : null;
  const senales: Par[] = [
    { k: "Dirección IP", v: r.ip },
    { k: "Ubicación aproximada", v: r.ubic },
    { k: "Huella de dispositivo", v: r.huella },
    { k: "Dispositivo", v: r.disp },
    {
      k: "Horario",
      v: done
        ? `Iniciada ${hm(t0)} · enviada ${hm(t0 + E.cum[4])}${nocturno ? " · horario nocturno" : ""}`
        : `Iniciada ${hm(t0)} · sin enviar`,
    },
    { k: "Ritmo de escritura", v: r.ritmo },
    { k: "Duración total", v: done ? `${Math.floor(E.cum[4] / 60)} min ${p2(E.cum[4] % 60)} s` : "En curso" },
  ];

  return {
    numero: numeroDe(r),
    fecha: r.f + " · " + hm(fin(r)),
    estado: estadoSolicitud(r, true),
    riesgo: rk,
    regla: rk.key === "bajo" ? "Regla aplicada: R-01 · Monto mensual bajo" : "Ninguna regla aplicada",
    explicacion,
    basicos,
    ingresos,
    movimiento,
    senales,
    pasos,
    timeline,
  };
}
