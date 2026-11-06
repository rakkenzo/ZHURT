import './globals.css';
import type { ReactNode } from 'react';

export const metadata = { title: 'Жұрт', description: 'Отбасылық мессенджер · Семейный мессенджер' };

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="kk">
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <meta name="theme-color" content="#0D1712" />
        <link rel="icon" href="/favicon.svg" />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;700&family=Unbounded:wght@500;700&display=swap&subset=cyrillic,cyrillic-ext" rel="stylesheet" />
      </head>
      <body>{children}</body>
    </html>
  );
}
