import { env } from 'cloudflare:workers';
import { handleBoard } from '@/lib/board.mjs';
export const dynamic = 'force-dynamic';
export function GET(request: Request) { return handleBoard(request, env); }
export function POST(request: Request) { return handleBoard(request, env); }
