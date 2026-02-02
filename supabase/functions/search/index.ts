// Supabase Edge Function: Search articles
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? '',
      {
        global: {
          headers: { Authorization: req.headers.get('Authorization')! },
        },
      }
    )

    const url = new URL(req.url)
    const query = url.searchParams.get('q')
    const limit = parseInt(url.searchParams.get('limit') ?? '50')

    if (!query || query.trim().length < 2) {
      return new Response(
        JSON.stringify({ error: 'Query must be at least 2 characters' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    // Use the search_articles function for full-text search
    const { data, error } = await supabaseClient
      .rpc('search_articles', {
        p_query: query,
        p_limit: limit
      })

    if (error) {
      // Fallback to ilike search if FTS fails
      const { data: fallbackData, error: fallbackError } = await supabaseClient
        .from('articles')
        .select('*')
        .eq('moderation_status', 'approved')
        .or(`title.ilike.%${query}%,preview.ilike.%${query}%`)
        .order('published_at', { ascending: false })
        .limit(limit)

      if (fallbackError) {
        throw fallbackError
      }

      return new Response(
        JSON.stringify({ articles: fallbackData, total: fallbackData?.length ?? 0 }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    return new Response(
      JSON.stringify({ articles: data, total: data?.length ?? 0 }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  }
})
