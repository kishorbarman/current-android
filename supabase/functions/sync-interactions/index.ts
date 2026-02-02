// Supabase Edge Function: Sync interactions from client
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

interface Interaction {
  id: string
  user_id: string
  article_id: string
  interaction_type: string
  metadata?: Record<string, any>
  created_at: string
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

    const {
      data: { user },
      error: authError,
    } = await supabaseClient.auth.getUser()

    if (authError || !user) {
      return new Response(
        JSON.stringify({ error: 'Unauthorized' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    const { interactions } = await req.json() as { interactions: Interaction[] }

    if (!interactions || !Array.isArray(interactions)) {
      return new Response(
        JSON.stringify({ error: 'Invalid request body' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    // Validate all interactions belong to the authenticated user
    const validInteractions = interactions.filter(i => i.user_id === user.id)

    if (validInteractions.length === 0) {
      return new Response(
        JSON.stringify({ message: 'No valid interactions to sync', synced: 0 }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    // Insert interactions (upsert to handle duplicates)
    const { error } = await supabaseClient
      .from('interactions')
      .upsert(
        validInteractions.map(i => ({
          id: i.id,
          user_id: i.user_id,
          article_id: i.article_id,
          interaction_type: i.interaction_type,
          metadata: i.metadata,
          created_at: i.created_at,
        })),
        { onConflict: 'id' }
      )

    if (error) {
      throw error
    }

    return new Response(
      JSON.stringify({
        message: 'Interactions synced successfully',
        synced: validInteractions.length
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  }
})
