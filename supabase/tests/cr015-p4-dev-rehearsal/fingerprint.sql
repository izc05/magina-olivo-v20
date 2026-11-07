CREATE FUNCTION pg_temp.fp() RETURNS TABLE(tbl text, n bigint, h text) LANGUAGE plpgsql AS $$
DECLARE r record;
BEGIN
  FOR r IN SELECT n.nspname s, c.relname t FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
           WHERE c.relkind IN ('r','p') AND n.nspname IN ('public','auth','storage','magina_g2_private','supabase_migrations')
           ORDER BY 1,2 LOOP
    tbl := r.s||'.'||r.t;
    EXECUTE format('SELECT count(*), md5(coalesce(string_agg(x::text, %L ORDER BY x::text), %L)) FROM %I.%I x', '|', '', r.s, r.t) INTO n, h;
    RETURN NEXT;
  END LOOP;
END $$;
SELECT tbl||' '||n||' '||h FROM pg_temp.fp();
