import { integer, sqliteTable, text, primaryKey, index } from "drizzle-orm/sqlite-core";
export const posts = sqliteTable("posts", {
  id: text("id").primaryKey(), author: text("author").notNull(), nickname: text("nickname").notNull(),
  body: text("body").notNull(), salt: text("salt").notNull(), passwordHash: text("password_hash").notNull(),
  mediaKey: text("media_key").notNull(), mime: text("mime").notNull(), bytes: integer("bytes").notNull(),
  duration: integer("duration").notNull().default(0), created: integer("created").notNull(),
  state: text("state").notNull().default("active")
}, t => [index("posts_feed").on(t.state, t.created)]);
export const comments = sqliteTable("comments", {
  id: text("id").primaryKey(), postId: text("post_id").notNull().references(() => posts.id),
  author: text("author").notNull(), nickname: text("nickname").notNull(), body: text("body").notNull(),
  salt: text("salt").notNull(), passwordHash: text("password_hash").notNull(), created: integer("created").notNull(),
  state: text("state").notNull().default("active")
}, t => [index("comments_post").on(t.postId, t.created)]);
export const likes = sqliteTable("likes", {
  postId: text("post_id").notNull().references(() => posts.id), visitor: text("visitor").notNull()
}, t => [primaryKey({columns:[t.postId,t.visitor]})]);
export const blocks = sqliteTable("blocks", {
  visitor: text("visitor").notNull(), author: text("author").notNull(), created: integer("created").notNull()
}, t => [primaryKey({columns:[t.visitor,t.author]})]);
export const reports = sqliteTable("reports", {
  id: text("id").primaryKey(), kind: text("kind").notNull(), target: text("target").notNull(),
  visitor: text("visitor").notNull(), reason: text("reason").notNull(), created: integer("created").notNull(),
  resolved: integer("resolved").notNull().default(0)
}, t => [index("reports_pending").on(t.resolved,t.created)]);
export const limits = sqliteTable("rate_limits", {
  key: text("key").primaryKey(), count: integer("count").notNull(), expires: integer("expires").notNull()
});
export const settings = sqliteTable("settings", { key: text("key").primaryKey(), value: text("value").notNull() });

