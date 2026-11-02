import type { Metadata } from "next";
import "./globals.css";
import "./board.css";

export const metadata: Metadata = {
  title: "우리 아이 자랑 · 아가톡",
  description: "아가말 영상카드와 사진으로 함께 웃는 순간. 익명으로 나누는 우리 아이 자랑.",
  robots: { index: false, follow: false },
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body className="antialiased">{children}</body>
    </html>
  );
}
