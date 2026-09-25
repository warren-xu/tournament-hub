-- Riot places each agent on the same 2048x1860 canvas but does not centre them on it,
-- so a portrait centred by geometry still looks off-centre. Neon sits 9.4% right of
-- centre, Killjoy 5.1% left - about 14 points of spread across the roster, which no
-- single CSS offset can correct.
--
-- portrait_focus_x is where the agent's body actually sits, as a fraction of image
-- width. The card shifts each portrait by its own amount. Measured from the alpha
-- channel: the bounding box of pixels with alpha > 160, so faint smoke and scarves
-- (Jett's, notably) do not drag the result around.
--
-- The sync deliberately leaves this column alone; a newly released agent simply
-- defaults to dead centre until someone measures it.
alter table agents add column portrait_focus_x real not null default 0.5;

update agents set portrait_focus_x = 0.4629 where name = 'Astra';
update agents set portrait_focus_x = 0.4670 where name = 'Breach';
update agents set portrait_focus_x = 0.4937 where name = 'Brimstone';
update agents set portrait_focus_x = 0.5117 where name = 'Chamber';
update agents set portrait_focus_x = 0.4714 where name = 'Clove';
update agents set portrait_focus_x = 0.5508 where name = 'Deadlock';
update agents set portrait_focus_x = 0.5085 where name = 'Fade';
update agents set portrait_focus_x = 0.4851 where name = 'Harbor';
update agents set portrait_focus_x = 0.4846 where name = 'Iso';
update agents set portrait_focus_x = 0.5632 where name = 'Jett';
update agents set portrait_focus_x = 0.4495 where name = 'Killjoy';
update agents set portrait_focus_x = 0.5291 where name = 'Miks';
update agents set portrait_focus_x = 0.5945 where name = 'Neon';
update agents set portrait_focus_x = 0.5188 where name = 'Phoenix';
update agents set portrait_focus_x = 0.5615 where name = 'Raze';
update agents set portrait_focus_x = 0.4578 where name = 'Reyna';
update agents set portrait_focus_x = 0.5344 where name = 'Sage';
update agents set portrait_focus_x = 0.5156 where name = 'Sova';
update agents set portrait_focus_x = 0.5083 where name = 'Veto';
update agents set portrait_focus_x = 0.5305 where name = 'Viper';
update agents set portrait_focus_x = 0.4910 where name = 'Vyse';
update agents set portrait_focus_x = 0.5818 where name = 'Waylay';
update agents set portrait_focus_x = 0.5083 where name = 'Yoru';
