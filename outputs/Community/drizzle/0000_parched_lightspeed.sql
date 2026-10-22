CREATE TABLE `blocks` (
	`visitor` text NOT NULL,
	`author` text NOT NULL,
	`created` integer NOT NULL,
	PRIMARY KEY(`visitor`, `author`)
);
--> statement-breakpoint
CREATE TABLE `comments` (
	`id` text PRIMARY KEY NOT NULL,
	`post_id` text NOT NULL,
	`author` text NOT NULL,
	`nickname` text NOT NULL,
	`body` text NOT NULL,
	`salt` text NOT NULL,
	`password_hash` text NOT NULL,
	`created` integer NOT NULL,
	`state` text DEFAULT 'active' NOT NULL,
	FOREIGN KEY (`post_id`) REFERENCES `posts`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `comments_post` ON `comments` (`post_id`,`created`);--> statement-breakpoint
CREATE TABLE `likes` (
	`post_id` text NOT NULL,
	`visitor` text NOT NULL,
	PRIMARY KEY(`post_id`, `visitor`),
	FOREIGN KEY (`post_id`) REFERENCES `posts`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE TABLE `rate_limits` (
	`key` text PRIMARY KEY NOT NULL,
	`count` integer NOT NULL,
	`expires` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `posts` (
	`id` text PRIMARY KEY NOT NULL,
	`author` text NOT NULL,
	`nickname` text NOT NULL,
	`body` text NOT NULL,
	`salt` text NOT NULL,
	`password_hash` text NOT NULL,
	`media_key` text NOT NULL,
	`mime` text NOT NULL,
	`bytes` integer NOT NULL,
	`duration` integer DEFAULT 0 NOT NULL,
	`created` integer NOT NULL,
	`state` text DEFAULT 'active' NOT NULL
);
--> statement-breakpoint
CREATE INDEX `posts_feed` ON `posts` (`state`,`created`);--> statement-breakpoint
CREATE TABLE `reports` (
	`id` text PRIMARY KEY NOT NULL,
	`kind` text NOT NULL,
	`target` text NOT NULL,
	`visitor` text NOT NULL,
	`reason` text NOT NULL,
	`created` integer NOT NULL,
	`resolved` integer DEFAULT 0 NOT NULL
);
--> statement-breakpoint
CREATE INDEX `reports_pending` ON `reports` (`resolved`,`created`);--> statement-breakpoint
CREATE TABLE `settings` (
	`key` text PRIMARY KEY NOT NULL,
	`value` text NOT NULL
);
