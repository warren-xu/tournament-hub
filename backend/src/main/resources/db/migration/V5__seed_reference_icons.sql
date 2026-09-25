-- Backfills icons and colours onto the rows seeded by V2 and V4, so a fresh database
-- renders correctly before anyone presses Sync on /admin/game-data.
--
-- Generated from valorant-api.com. The sync still corrects these and adds anything
-- released since, so this only has to be good enough for a first run.

update agents set external_id = '41fb69c1-4189-7b37-f117-bcaf1e96f1bf', icon_url = 'https://media.valorant-api.com/agents/41fb69c1-4189-7b37-f117-bcaf1e96f1bf/displayicon.png' where name = 'Astra';
update agents set external_id = '5f8d3a7f-467b-97f3-062c-13acf203c006', icon_url = 'https://media.valorant-api.com/agents/5f8d3a7f-467b-97f3-062c-13acf203c006/displayicon.png' where name = 'Breach';
update agents set external_id = '9f0d8ba9-4140-b941-57d3-a7ad57c6b417', icon_url = 'https://media.valorant-api.com/agents/9f0d8ba9-4140-b941-57d3-a7ad57c6b417/displayicon.png' where name = 'Brimstone';
update agents set external_id = '22697a3d-45bf-8dd7-4fec-84a9e28c69d7', icon_url = 'https://media.valorant-api.com/agents/22697a3d-45bf-8dd7-4fec-84a9e28c69d7/displayicon.png' where name = 'Chamber';
update agents set external_id = '1dbf2edd-4729-0984-3115-daa5eed44993', icon_url = 'https://media.valorant-api.com/agents/1dbf2edd-4729-0984-3115-daa5eed44993/displayicon.png' where name = 'Clove';
update agents set external_id = '117ed9e3-49f3-6512-3ccf-0cada7e3823b', icon_url = 'https://media.valorant-api.com/agents/117ed9e3-49f3-6512-3ccf-0cada7e3823b/displayicon.png' where name = 'Cypher';
update agents set external_id = 'cc8b64c8-4b25-4ff9-6e7f-37b4da43d235', icon_url = 'https://media.valorant-api.com/agents/cc8b64c8-4b25-4ff9-6e7f-37b4da43d235/displayicon.png' where name = 'Deadlock';
update agents set external_id = 'dade69b4-4f5a-8528-247b-219e5a1facd6', icon_url = 'https://media.valorant-api.com/agents/dade69b4-4f5a-8528-247b-219e5a1facd6/displayicon.png' where name = 'Fade';
update agents set external_id = 'e370fa57-4757-3604-3648-499e1f642d3f', icon_url = 'https://media.valorant-api.com/agents/e370fa57-4757-3604-3648-499e1f642d3f/displayicon.png' where name = 'Gekko';
update agents set external_id = '95b78ed7-4637-86d9-7e41-71ba8c293152', icon_url = 'https://media.valorant-api.com/agents/95b78ed7-4637-86d9-7e41-71ba8c293152/displayicon.png' where name = 'Harbor';
update agents set external_id = '0e38b510-41a8-5780-5e8f-568b2a4f2d6c', icon_url = 'https://media.valorant-api.com/agents/0e38b510-41a8-5780-5e8f-568b2a4f2d6c/displayicon.png' where name = 'Iso';
update agents set external_id = 'add6443a-41bd-e414-f6ad-e58d267f4e95', icon_url = 'https://media.valorant-api.com/agents/add6443a-41bd-e414-f6ad-e58d267f4e95/displayicon.png' where name = 'Jett';
update agents set external_id = '601dbbe7-43ce-be57-2a40-4abd24953621', icon_url = 'https://media.valorant-api.com/agents/601dbbe7-43ce-be57-2a40-4abd24953621/displayicon.png' where name = 'KAY/O';
update agents set external_id = '1e58de9c-4950-5125-93e9-a0aee9f98746', icon_url = 'https://media.valorant-api.com/agents/1e58de9c-4950-5125-93e9-a0aee9f98746/displayicon.png' where name = 'Killjoy';
update agents set external_id = 'bb2a4828-46eb-8cd1-e765-15848195d751', icon_url = 'https://media.valorant-api.com/agents/bb2a4828-46eb-8cd1-e765-15848195d751/displayicon.png' where name = 'Neon';
update agents set external_id = '8e253930-4c05-31dd-1b6c-968525494517', icon_url = 'https://media.valorant-api.com/agents/8e253930-4c05-31dd-1b6c-968525494517/displayicon.png' where name = 'Omen';
update agents set external_id = 'eb93336a-449b-9c1b-0a54-a891f7921d69', icon_url = 'https://media.valorant-api.com/agents/eb93336a-449b-9c1b-0a54-a891f7921d69/displayicon.png' where name = 'Phoenix';
update agents set external_id = 'f94c3b30-42be-e959-889c-5aa313dba261', icon_url = 'https://media.valorant-api.com/agents/f94c3b30-42be-e959-889c-5aa313dba261/displayicon.png' where name = 'Raze';
update agents set external_id = 'a3bfb853-43b2-7238-a4f1-ad90e9e46bcc', icon_url = 'https://media.valorant-api.com/agents/a3bfb853-43b2-7238-a4f1-ad90e9e46bcc/displayicon.png' where name = 'Reyna';
update agents set external_id = '569fdd95-4d10-43ab-ca70-79becc718b46', icon_url = 'https://media.valorant-api.com/agents/569fdd95-4d10-43ab-ca70-79becc718b46/displayicon.png' where name = 'Sage';
update agents set external_id = '6f2a04ca-43e0-be17-7f36-b3908627744d', icon_url = 'https://media.valorant-api.com/agents/6f2a04ca-43e0-be17-7f36-b3908627744d/displayicon.png' where name = 'Skye';
update agents set external_id = '320b2a48-4d9b-a075-30f1-1f93a9b638fa', icon_url = 'https://media.valorant-api.com/agents/320b2a48-4d9b-a075-30f1-1f93a9b638fa/displayicon.png' where name = 'Sova';
update agents set external_id = 'b444168c-4e35-8076-db47-ef9bf368f384', icon_url = 'https://media.valorant-api.com/agents/b444168c-4e35-8076-db47-ef9bf368f384/displayicon.png' where name = 'Tejo';
update agents set external_id = '707eab51-4836-f488-046a-cda6bf494859', icon_url = 'https://media.valorant-api.com/agents/707eab51-4836-f488-046a-cda6bf494859/displayicon.png' where name = 'Viper';
update agents set external_id = 'efba5359-4016-a1e5-7626-b1ae76895940', icon_url = 'https://media.valorant-api.com/agents/efba5359-4016-a1e5-7626-b1ae76895940/displayicon.png' where name = 'Vyse';
update agents set external_id = 'df1cb487-4902-002e-5c17-d28e83e78588', icon_url = 'https://media.valorant-api.com/agents/df1cb487-4902-002e-5c17-d28e83e78588/displayicon.png' where name = 'Waylay';
update agents set external_id = '7f94d92c-4234-0a36-9646-3a87eb8b5c89', icon_url = 'https://media.valorant-api.com/agents/7f94d92c-4234-0a36-9646-3a87eb8b5c89/displayicon.png' where name = 'Yoru';

update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/0/smallicon.png', color = 'ffffffff' where tier = 0;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/3/smallicon.png', color = '868986ff' where tier = 3;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/4/smallicon.png', color = '868986ff' where tier = 4;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/5/smallicon.png', color = '868986ff' where tier = 5;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/6/smallicon.png', color = 'a5855dff' where tier = 6;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/7/smallicon.png', color = 'a5855dff' where tier = 7;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/8/smallicon.png', color = 'a5855dff' where tier = 8;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/9/smallicon.png', color = 'bbc2c2ff' where tier = 9;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/10/smallicon.png', color = 'bbc2c2ff' where tier = 10;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/11/smallicon.png', color = 'bbc2c2ff' where tier = 11;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/12/smallicon.png', color = 'eccf56ff' where tier = 12;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/13/smallicon.png', color = 'eccf56ff' where tier = 13;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/14/smallicon.png', color = 'eccf56ff' where tier = 14;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/15/smallicon.png', color = '59a9b6ff' where tier = 15;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/16/smallicon.png', color = '59a9b6ff' where tier = 16;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/17/smallicon.png', color = '59a9b6ff' where tier = 17;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/18/smallicon.png', color = 'b489c4ff' where tier = 18;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/19/smallicon.png', color = 'b489c4ff' where tier = 19;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/20/smallicon.png', color = 'b489c4ff' where tier = 20;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/21/smallicon.png', color = '6ae2afff' where tier = 21;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/22/smallicon.png', color = '6ae2afff' where tier = 22;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/23/smallicon.png', color = '6ae2afff' where tier = 23;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/24/smallicon.png', color = 'bb3d65ff' where tier = 24;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/25/smallicon.png', color = 'bb3d65ff' where tier = 25;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/26/smallicon.png', color = 'bb3d65ff' where tier = 26;
update ranks set icon_url = 'https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/27/smallicon.png', color = 'ffffaaff' where tier = 27;
