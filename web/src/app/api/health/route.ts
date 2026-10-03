export const dynamic = "force-static";

export async function GET() {
  return Response.json(
    {
      status: "ok",
      service: "magina-olivo-web",
    },
    {
      headers: {
        "cache-control": "no-store",
      },
    },
  );
}
