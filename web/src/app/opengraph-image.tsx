import { ImageResponse } from "next/og";

export const alt = "Mágina Olivo · Más que olivos, nuestra tierra";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpenGraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          position: "relative",
          overflow: "hidden",
          background: "#f3efe3",
          color: "#153022",
          fontFamily: "Georgia, serif",
        }}
      >
        <div
          style={{
            position: "absolute",
            inset: 0,
            background:
              "radial-gradient(circle at 75% 22%, rgba(185,154,96,.35), transparent 24%), linear-gradient(145deg,#f8f4e9 0%,#e6e0cf 52%,#7d8a69 53%,#324634 100%)",
          }}
        />
        <div
          style={{
            position: "absolute",
            right: -80,
            bottom: -70,
            width: 560,
            height: 360,
            borderRadius: "55% 45% 0 0",
            background: "#53673e",
            opacity: 0.76,
          }}
        />
        <div
          style={{
            position: "relative",
            width: "64%",
            height: "100%",
            display: "flex",
            flexDirection: "column",
            justifyContent: "center",
            padding: "72px 78px",
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 18,
              marginBottom: 46,
              fontSize: 34,
              fontWeight: 700,
            }}
          >
            <div
              style={{
                width: 52,
                height: 52,
                borderRadius: "70% 30% 70% 30%",
                background: "#53673e",
                transform: "rotate(-24deg)",
              }}
            />
            <span>Mágina Olivo</span>
          </div>
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              fontSize: 86,
              lineHeight: 0.93,
              letterSpacing: "-4px",
            }}
          >
            <span>Tu olivar,</span>
            <span>en buenas manos.</span>
          </div>
          <div
            style={{
              marginTop: 34,
              fontFamily: "Arial, sans-serif",
              fontSize: 24,
              lineHeight: 1.45,
              color: "#536157",
            }}
          >
            Gestión del olivar, campañas y territorio en una experiencia clara
            y cercana.
          </div>
          <div
            style={{
              marginTop: 38,
              fontSize: 28,
              fontStyle: "italic",
              color: "#53673e",
            }}
          >
            Más que olivos, nuestra tierra.
          </div>
        </div>
      </div>
    ),
    size,
  );
}
