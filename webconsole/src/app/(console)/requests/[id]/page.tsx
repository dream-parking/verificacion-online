import { RequestDetailView } from "./RequestDetailView";

export default async function RequestDetailPage({ params }: PageProps<"/requests/[id]">) {
  const { id } = await params;
  return <RequestDetailView id={id} />;
}
