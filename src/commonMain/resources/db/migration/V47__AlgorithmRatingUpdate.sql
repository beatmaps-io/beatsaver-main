UPDATE beatmap
SET score = upvote / (upvote + downvote)::numeric - (upvote / (upvote + downvote)::numeric - 0.5) * POWER(2.0, -log(3, ((upvote + downvote) / 2.0) + 1))
WHERE upvote + downvote > 0