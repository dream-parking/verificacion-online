import { DetalleView } from "./DetalleView";

export default async function DetalleSolicitudPage({ params }: PageProps<"/solicitudes/[id]">) {
  const { id } = await params;
  return <DetalleView id={id} />;
}
